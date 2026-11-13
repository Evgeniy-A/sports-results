import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { api } from "../api/client";
import type {
  EventDetails,
  PageResponse,
  ResultInquiryLookup,
  ResultListItem,
} from "../api/types";
import { useDebouncedValue } from "../hooks/useDebouncedValue";
import { formatDuration, formatLeaderGap, statusLabel } from "../utils/format";
import {
  defaultPublicResultSort,
  initialRaceSelection,
  publicResultRaces,
} from "../utils/publicEventView";
import {
  publicStandingPresentation,
  scoringTimeHeading,
} from "../utils/rankingPresentation";
import {
  LatestRequestGate,
  isRequestCancellation,
  loadPublicResultsAndInquiry,
  resolvePublicResultsAndInquiry,
  shouldRequestResultInquiry,
} from "../utils/resultInquiry";
import { Pagination } from "./Pagination";
import { RankingAchievements } from "./RankingAchievements";
import { ResultDetailsDialog } from "./ResultDetailsDialog";
import { ResultInquiryPanel } from "./ResultInquiryPanel";

const EMPTY_PAGE: PageResponse<ResultListItem> = {
  content: [],
  page: 0,
  size: 50,
  totalElements: 0,
  totalPages: 0,
  sort: "place",
  direction: "asc",
};

function inquiryBibCandidate(value: string): string {
  const normalized = value.trim();
  if (!normalized || /\s/.test(normalized) || !/\d/.test(normalized)) return "";
  return normalized;
}

export function EventResults({ event }: { event: EventDetails }) {
  const initialRaces = publicResultRaces(event.races);
  const initialRaceId = initialRaceSelection(initialRaces);
  const initialRace = initialRaces.find(
    (race) => String(race.id) === initialRaceId,
  );
  const [protocol, setProtocol] =
    useState<PageResponse<ResultListItem>>(EMPTY_PAGE);
  const [raceId, setRaceId] = useState(initialRaceId);
  const [gender, setGender] = useState("");
  const [categoryId, setCategoryId] = useState("");
  const [clusterId, setClusterId] = useState("");
  const [status, setStatus] = useState("");
  const [search, setSearch] = useState("");
  const [sort, setSort] = useState<string>(
    defaultPublicResultSort(initialRace?.rules?.rankingBasis),
  );
  const [direction, setDirection] = useState<"asc" | "desc">("asc");
  const [page, setPage] = useState(0);
  const [loading, setLoading] = useState(Boolean(initialRaceId));
  const [error, setError] = useState<string | null>(null);
  const [inquiryError, setInquiryError] = useState<string | null>(null);
  const [selectedResultId, setSelectedResultId] = useState<number | null>(null);
  const [inquiry, setInquiry] = useState<ResultInquiryLookup | null>(null);
  const [inquiryLoading, setInquiryLoading] = useState(false);
  const protocolRequestRef = useRef<AbortController | null>(null);
  const inquirySelectionRequestRef = useRef<AbortController | null>(null);
  const [protocolRequestGate] = useState(() => new LatestRequestGate());
  const debouncedSearch = useDebouncedValue(search, 350);
  const races = useMemo(() => publicResultRaces(event.races), [event.races]);
  const selectedRace = useMemo(
    () => races.find((race) => String(race.id) === raceId),
    [races, raceId],
  );
  const categories = selectedRace?.rules?.categoryEnabled
    ? selectedRace.rules.availableCategories.map((category) => ({
        id: category.id,
        name: category.name,
        rankingBasis: category.rankingBasis,
      }))
    : [];
  const showCategoryColumn = selectedRace?.rules?.categoryEnabled ?? false;
  const selectedCategory = categories.find(
    (category) => String(category.id) === categoryId,
  );
  const rankingBasis =
    selectedCategory?.rankingBasis ??
    selectedRace?.rules?.rankingBasis ??
    protocol.content[0]?.rankingBasis;
  const {
    categoryStandingSelected,
    showStanding: showStandingColumn,
    mode: rankingMode,
    title: standingColumnTitle,
  } = publicStandingPresentation(
    selectedRace?.rules?.primaryStandingMode,
    selectedRace?.rules?.categoryEnabled ?? false,
    categoryId,
  );
  const activeRankingBasis = showStandingColumn
    ? (rankingBasis ?? "NONE")
    : "NONE";
  const hasPublicResultRows = protocol.content.length > 0;

  const clearInquiryState = () => {
    protocolRequestGate.invalidate();
    protocolRequestRef.current?.abort();
    inquirySelectionRequestRef.current?.abort();
    setInquiry(null);
    setInquiryError(null);
    setInquiryLoading(false);
  };

  const selectPublicResultRace = useCallback(
    (lookup: ResultInquiryLookup) => {
      if (!lookup.raceId) return;
      if (String(lookup.raceId) === raceId) return;
      const targetRace = races.find((race) => race.id === lookup.raceId);
      if (!targetRace) return;

      setRaceId(String(targetRace.id));
      setProtocol(EMPTY_PAGE);
      setCategoryId("");
      setClusterId("");
      setStatus("");
      setSort(defaultPublicResultSort(targetRace.rules?.rankingBasis));
      setDirection("asc");
      setPage(0);
    },
    [
      races,
      raceId,
      setCategoryId,
      setClusterId,
      setDirection,
      setPage,
      setProtocol,
      setRaceId,
      setSort,
      setStatus,
    ],
  );

  useEffect(() => {
    protocolRequestGate.invalidate();
    protocolRequestRef.current?.abort();
    inquirySelectionRequestRef.current?.abort();
    // oxlint-disable-next-line react/set-state-in-effect -- an Event change invalidates all personal lookup state.
    setInquiry(null);
    setInquiryError(null);
    setSearch("");
  }, [event.id, protocolRequestGate]);

  useEffect(() => {
    if (!raceId) return;
    const requestId = protocolRequestGate.begin();
    const controller = new AbortController();
    protocolRequestRef.current = controller;
    const isCurrentRequest = () =>
      protocolRequestGate.isCurrent(requestId) &&
      protocolRequestRef.current === controller &&
      !controller.signal.aborted;
    // oxlint-disable-next-line react/set-state-in-effect -- loading starts with the external request.
    setLoading(true);
    setError(null);
    setInquiry(null);
    setInquiryError(null);
    setInquiryLoading(false);
    const searchQuery = debouncedSearch.trim();
    const inquiryBib = inquiryBibCandidate(searchQuery);

    const filters = {
      query: searchQuery || undefined,
      gender: gender || undefined,
      categoryId: categoryId ? Number(categoryId) : undefined,
      clusterId: clusterId ? Number(clusterId) : undefined,
      status: status || undefined,
    };

    const inquiryFilters = {
      ...filters,
      bib: inquiryBib || undefined,
    };

    const shouldLoadInquiry = shouldRequestResultInquiry(inquiryFilters);
    setInquiryLoading(shouldLoadInquiry);
    loadPublicResultsAndInquiry(
      inquiryFilters,
      () =>
        api.results(
          event.id,
          {
            raceId: Number(raceId),
            ...filters,
            sort,
            direction,
            page,
            size: 50,
          },
          controller.signal,
        ),
      () =>
        api.resultInquiry(
          event.id,
          inquiryBib,
          Number(raceId),
          controller.signal,
        ),
    )
      .then((outcome) => {
        if (!isCurrentRequest()) return;
        const resolution = resolvePublicResultsAndInquiry(outcome);
        if (resolution.state === "CANCELLED") return;
        if (resolution.state === "PUBLIC_RESULTS_FAILED") {
          console.error("Protocol request failed", resolution.error);
          setError("Не удалось получить протокол. Попробуйте ещё раз.");
          setInquiry(null);
          return;
        }
        setProtocol(resolution.publicResults);
        if (resolution.inquiryError) {
          console.error(
            "Result inquiry request failed",
            resolution.inquiryError,
          );
          setInquiryError(
            "Результаты загружены, но сейчас не удалось проверить возможность уточнения. Попробуйте повторить поиск.",
          );
        }
        const lookup = resolution.inquiry;
        if (!lookup) return;
        if (lookup.lookupState === "RESULT_PUBLIC") {
          selectPublicResultRace(lookup);
          setInquiry(null);
        } else {
          setInquiry(lookup);
        }
      })
      .catch((reason: unknown) => {
        if (isCurrentRequest() && !isRequestCancellation(reason)) {
          console.error("Protocol request failed", reason);
          setError("Не удалось получить протокол. Попробуйте ещё раз.");
        }
      })
      .finally(() => {
        if (isCurrentRequest()) {
          setLoading(false);
          setInquiryLoading(false);
        }
      });
    return () => {
      controller.abort();
      if (protocolRequestRef.current === controller)
        protocolRequestRef.current = null;
    };
  }, [
    event.id,
    raceId,
    debouncedSearch,
    gender,
    categoryId,
    clusterId,
    status,
    sort,
    direction,
    page,
    protocolRequestGate,
    selectPublicResultRace,
  ]);

  const selectInquiryRace = async (selectedRaceId: number) => {
    if (!inquiry) return;
    inquirySelectionRequestRef.current?.abort();
    const controller = new AbortController();
    inquirySelectionRequestRef.current = controller;
    setInquiryLoading(true);
    try {
      const selected = await api.resultInquiry(
        event.id,
        inquiry.bib,
        selectedRaceId,
        controller.signal,
      );
      if (controller.signal.aborted) return;
      if (selected.lookupState === "RESULT_PUBLIC") {
        selectPublicResultRace(selected);
        setInquiry(null);
      } else {
        setInquiry(selected);
      }
    } catch (reason: unknown) {
      if (!controller.signal.aborted) {
        console.error("Result inquiry race selection failed", reason);
        setInquiryError("Не удалось выбрать старт. Попробуйте ещё раз.");
      }
    } finally {
      if (!controller.signal.aborted) setInquiryLoading(false);
      if (inquirySelectionRequestRef.current === controller)
        inquirySelectionRequestRef.current = null;
    }
  };

  const resetPage = () => setPage(0);
  const changeRace = (nextRaceId: string) => {
    clearInquiryState();
    const nextRace = races.find(
      (candidate) => String(candidate.id) === nextRaceId,
    );
    setRaceId(nextRaceId);
    setProtocol(EMPTY_PAGE);
    setCategoryId("");
    setClusterId("");
    setSort(defaultPublicResultSort(nextRace?.rules?.rankingBasis));
    setDirection("asc");
    resetPage();
  };
  const resetFilters = () => {
    clearInquiryState();
    setGender("");
    setCategoryId("");
    setClusterId("");
    setStatus("");
    setSearch("");
    setPage(0);
  };

  return (
    <section className="results-panel" aria-labelledby="protocol-title">
      <div className="section-heading protocol-heading">
        <div>
          <p className="eyebrow">
            {showStandingColumn ? "Официальные данные" : "Результаты"}
          </p>
          <h2 id="protocol-title">
            {showStandingColumn ? "Протокол" : "Результаты"}
          </h2>
        </div>
        <div className="protocol-heading-actions">
          <a className="results-appeal-link" href="/appeal">
            <span>Есть вопрос по результату?</span>
            <strong>Подать обращение →</strong>
          </a>
        </div>
      </div>

      <section
        className="protocol-selection"
        aria-label={
          showStandingColumn
            ? "Выбор официального протокола"
            : "Выбор таблицы результатов"
        }
      >
        {races.length > 1 && (
          <div className="selection-group">
            <span>Старт</span>
            <div className="selection-buttons" role="group" aria-label="Старт">
              {races.map((race) => (
                <button
                  type="button"
                  key={race.id}
                  aria-pressed={String(race.id) === raceId}
                  onClick={() => changeRace(String(race.id))}
                >
                  {race.name}
                </button>
              ))}
            </div>
          </div>
        )}
        {selectedRace && (
          <p>
            <span>
              {showStandingColumn
                ? "Официальный протокол"
                : "Таблица результатов"}
            </span>
            <strong>{selectedRace.name}</strong>
          </p>
        )}
      </section>

      {selectedRace && (
        <div className="protocol-controls">
          <section
            className="control-section result-search-section"
            aria-labelledby="filters-title"
          >
            <div className="control-heading">
              <h3 id="filters-title">Фильтры</h3>
              <button
                className="filter-reset"
                type="button"
                onClick={resetFilters}
              >
                Сбросить фильтры
              </button>
            </div>
            <div
              className="result-primary-filters"
              aria-label="Основные фильтры результатов"
            >
              <label className="result-search-field">
                <span>Поиск</span>
                <input
                  aria-label="Поиск по ФИО, стартовому номеру или названию команды"
                  value={search}
                  onChange={(changeEvent) => {
                    clearInquiryState();
                    setSearch(changeEvent.target.value);
                    resetPage();
                  }}
                  placeholder="ФИО, стартовый номер или название команды"
                />
              </label>
              <div className="gender-filter">
                <span>Участники</span>
                <div role="group" aria-label="Пол участника">
                  <button
                    type="button"
                    aria-pressed={gender === ""}
                    onClick={() => {
                      clearInquiryState();
                      setGender("");
                      resetPage();
                    }}
                  >
                    Все участники
                  </button>
                  <button
                    type="button"
                    aria-pressed={gender === "male"}
                    onClick={() => {
                      clearInquiryState();
                      setGender("male");
                      resetPage();
                    }}
                  >
                    Мужчины
                  </button>
                  <button
                    type="button"
                    aria-pressed={gender === "female"}
                    onClick={() => {
                      clearInquiryState();
                      setGender("female");
                      resetPage();
                    }}
                  >
                    Женщины
                  </button>
                </div>
              </div>
            </div>
            <div className="advanced-filters">
              <div
                className="result-filters"
                aria-label={
                  showStandingColumn
                    ? "Фильтры протокола"
                    : "Фильтры результатов"
                }
              >
                {selectedRace.clusters.length >= 2 && (
                  <label>
                    <span>Стартовая волна</span>
                    <select
                      aria-label="Стартовая волна"
                      value={clusterId}
                      onChange={(changeEvent) => {
                        clearInquiryState();
                        setClusterId(changeEvent.target.value);
                        resetPage();
                      }}
                    >
                      <option value="">Все стартовые волны</option>
                      {selectedRace.clusters.map((cluster) => (
                        <option key={cluster.id} value={cluster.id}>
                          {cluster.displayName}
                        </option>
                      ))}
                    </select>
                  </label>
                )}

                {selectedRace.rules?.categoryEnabled && (
                  <label>
                    <span>Категория</span>
                    <select
                      aria-label="Категория"
                      value={categoryId}
                      onChange={(changeEvent) => {
                        clearInquiryState();
                        const nextCategoryId = changeEvent.target.value;
                        const nextCategory = categories.find(
                          (category) => String(category.id) === nextCategoryId,
                        );

                        setCategoryId(nextCategoryId);
                        setSort(
                          defaultPublicResultSort(
                            nextCategory?.rankingBasis ??
                              selectedRace.rules?.rankingBasis,
                          ),
                        );
                        setDirection("asc");
                        resetPage();
                      }}
                    >
                      <option value="">Все категории</option>
                      {categories.map((category) => (
                        <option key={category.id} value={category.id}>
                          {category.name}
                        </option>
                      ))}
                    </select>
                  </label>
                )}

                <label>
                  <span>Статус</span>
                  <select
                    aria-label="Статус"
                    value={status}
                    onChange={(changeEvent) => {
                      clearInquiryState();
                      setStatus(changeEvent.target.value);
                      resetPage();
                    }}
                  >
                    <option value="">
                      Финишировавшие и дисквалифицированные
                    </option>
                    <option value="finished">Финишировавшие</option>
                    <option value="disqualified">Дисквалифицированные</option>
                  </select>
                </label>
              </div>
            </div>
          </section>
          <section
            className="control-section sorting-section"
            aria-labelledby="sorting-title"
          >
            <h3 id="sorting-title">Сортировать по</h3>
            <div className="sorting-controls">
              <label>
                <span>Поле</span>
                <select
                  aria-label="Сортировка"
                  value={sort}
                  onChange={(changeEvent) => {
                    clearInquiryState();
                    setSort(changeEvent.target.value);
                    resetPage();
                  }}
                >
                  <option value="gunTime">
                    Официальное время
                    {activeRankingBasis === "GUN_TIME" ? " · Зачёт" : ""}
                  </option>
                  <option value="chipTime">
                    Чистое время
                    {activeRankingBasis === "CHIP_TIME" ? " · Зачёт" : ""}
                  </option>
                  <option value="displayName">ФИО</option>
                  <option value="bib">Стартовый номер</option>
                </select>
              </label>
              <button
                className="direction-button"
                type="button"
                onClick={() => {
                  clearInquiryState();
                  setDirection(direction === "asc" ? "desc" : "asc");
                  resetPage();
                }}
              >
                {direction === "asc" ? "По возрастанию ↑" : "По убыванию ↓"}
              </button>
            </div>
          </section>
        </div>
      )}

      {selectedRace &&
        (showStandingColumn ? (
          <div className="context-note">
            <strong>
              {activeRankingBasis === "CHIP_TIME"
                ? "Зачёт по чистому времени"
                : "Зачёт по официальному времени"}
            </strong>
            {categoryStandingSelected
              ? " внутри выбранной возрастной категории"
              : " внутри выбранного старта"}{" "}
            не меняется от фильтров или сортировки. Выбранная сортировка
            управляет только порядком строк.
          </div>
        ) : (
          <div className="context-note">
            <strong>Официальный зачёт не настроен.</strong> Фильтры и сортировка
            управляют только порядком строк в таблице результатов.
          </div>
        ))}
      {error && (
        <div className="state-message error" role="alert">
          {error}
        </div>
      )}
      {!error && inquiryError && (
        <div className="state-message" role="status">
          {inquiryError}
        </div>
      )}
      {!error && !selectedRace && !loading && (
        <div className="state-message">
          {races.length === 0
            ? "Результаты ещё не опубликованы."
            : "Выберите старт."}
        </div>
      )}
      {selectedRace && loading && (
        <div className="state-message" aria-live="polite">
          {inquiryLoading
            ? "Проверяем стартовый номер…"
            : showStandingColumn
              ? "Загружаем протокол…"
              : "Загружаем результаты…"}
        </div>
      )}
      {selectedRace &&
        !loading &&
        !error &&
        protocol.content.length === 0 &&
        !inquiry && (
          <div className="state-message">
            По выбранным условиям результатов нет.
          </div>
        )}
      {selectedRace &&
        !loading &&
        !error &&
        inquiry &&
        !hasPublicResultRows && (
          <ResultInquiryPanel
            key={`${event.id}:${inquiry.bib}:${inquiry.lookupState}`}
            eventId={event.id}
            eventName={event.name}
            eventTimeZone={event.timeZone}
            inquiry={inquiry}
            selectingRace={inquiryLoading}
            onSelectRace={selectInquiryRace}
          />
        )}

      {selectedRace && !loading && !error && protocol.content.length > 0 && (
        <>
          <div className="table-wrap desktop-results">
            <table>
              <thead>
                <tr>
                  {showStandingColumn && <th>{standingColumnTitle}</th>}
                  <th>Стартовый №</th>
                  <th>Участник</th>
                  <th>Статус</th>
                  <th>
                    <span className="time-heading">
                      {scoringTimeHeading(activeRankingBasis, "GUN_TIME")}
                    </span>
                  </th>
                  <th>
                    <span className="time-heading">
                      {scoringTimeHeading(activeRankingBasis, "CHIP_TIME")}
                    </span>
                  </th>
                  {showCategoryColumn && <th>Категория</th>}
                </tr>
              </thead>
              <tbody>
                {protocol.content.map((result) => (
                  <tr
                    key={result.resultId}
                    tabIndex={0}
                    onClick={() => setSelectedResultId(result.resultId)}
                    onKeyDown={(keyboardEvent) => {
                      if (keyboardEvent.key === "Enter")
                        setSelectedResultId(result.resultId);
                    }}
                  >
                    {showStandingColumn && (
                      <td className="ranking-cell">
                        <RankingAchievements
                          achievements={result.rankingAchievements}
                          mode={rankingMode}
                        />
                      </td>
                    )}
                    <td>
                      <span className="bib">{result.bib ?? "—"}</span>
                    </td>
                    <td className="participant">{result.displayName}</td>
                    <td>
                      <span className={`status status-${result.status}`}>
                        {statusLabel(result.status)}
                      </span>
                    </td>
                    <td
                      className={`time ${activeRankingBasis === "GUN_TIME" ? "time-primary" : ""}`}
                    >
                      <ResultTime
                        value={result.gunTimeMs}
                        gapSeconds={result.gunLeaderGapSeconds}
                      />
                    </td>
                    <td
                      className={`time ${activeRankingBasis === "CHIP_TIME" ? "time-primary" : ""}`}
                    >
                      <ResultTime
                        value={result.chipTimeMs}
                        gapSeconds={result.chipLeaderGapSeconds}
                      />
                    </td>
                    {showCategoryColumn && (
                      <td>{result.category?.name ?? "—"}</td>
                    )}
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <div className="mobile-results">
            {protocol.content.map((result) => {
              const useChipTime =
                activeRankingBasis === "CHIP_TIME" ||
                (activeRankingBasis === "NONE" && sort === "chipTime");
              return (
                <button
                  className="result-card"
                  type="button"
                  key={result.resultId}
                  onClick={() => setSelectedResultId(result.resultId)}
                >
                  <span>
                    <strong>{result.displayName}</strong>
                    <small>
                      № {result.bib ?? "—"} · {selectedRace.name}
                      {showCategoryColumn && result.category
                        ? ` · ${result.category.name}`
                        : ""}
                    </small>
                  </span>
                  <span
                    className={`time ${showStandingColumn ? "time-primary" : ""}`}
                  >
                    <ResultTime
                      value={useChipTime ? result.chipTimeMs : result.gunTimeMs}
                      gapSeconds={
                        useChipTime
                          ? result.chipLeaderGapSeconds
                          : result.gunLeaderGapSeconds
                      }
                    />
                  </span>
                  {showStandingColumn && (
                    <span className="card-achievements">
                      <RankingAchievements
                        achievements={result.rankingAchievements}
                        mode={rankingMode}
                      />
                    </span>
                  )}
                </button>
              );
            })}
          </div>
        </>
      )}

      {selectedRace && (
        <Pagination
          page={protocol.page}
          totalPages={protocol.totalPages}
          onChange={setPage}
        />
      )}
      {selectedResultId !== null && selectedRace && (
        <ResultDetailsDialog
          resultId={selectedResultId}
          eventName={event.name}
          eventTimeZone={event.timeZone}
          startName={selectedRace.name}
          categoryEnabled={showCategoryColumn}
          showOfficialStanding={showStandingColumn}
          officialStandingTitle={standingColumnTitle}
          activeRankingBasis={activeRankingBasis}
          rankingMode={rankingMode}
          onClose={() => setSelectedResultId(null)}
        />
      )}
    </section>
  );
}

function ResultTime({
  value,
  gapSeconds,
}: {
  value: number | null;
  gapSeconds: number | null;
}) {
  const gap = formatLeaderGap(gapSeconds);
  return (
    <span className="result-time">
      <span>{formatDuration(value)}</span>
      {gap !== null && <small className="leader-gap">{gap}</small>}
    </span>
  );
}
