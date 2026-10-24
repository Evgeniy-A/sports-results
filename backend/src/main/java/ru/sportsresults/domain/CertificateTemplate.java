package ru.sportsresults.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;

@Entity
@Table(name = "certificate_templates")
public class CertificateTemplate extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;
    @Column(name = "display_name", nullable = false, length = 255) private String displayName;
    @Column(nullable = false) private boolean active = true;
    @Column(name = "display_order", nullable = false) private int displayOrder;
    @Column(name = "original_filename", nullable = false, length = 255) private String originalFilename;
    @Enumerated(EnumType.STRING) @Column(name = "storage_provider", nullable = false, length = 32) private EventDocumentStorageProvider storageProvider;
    @Column(name = "storage_key", nullable = false, length = 1024) private String storageKey;
    @Column(name = "content_type", nullable = false, length = 100) private String contentType;
    @Column(name = "size_bytes", nullable = false) private long sizeBytes;
    @Column(nullable = false, length = 64) private String sha256;
    @Column(name = "uploaded_at", nullable = false) private Instant uploadedAt;
    @Column(name = "uploaded_by", nullable = false, length = 160) private String uploadedBy;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(name = "certificate_template_races",
            joinColumns = @JoinColumn(name = "template_id"),
            inverseJoinColumns = @JoinColumn(name = "race_id"))
    private Set<Race> races = new LinkedHashSet<>();

    @Column(name="participant_x", nullable=false) private BigDecimal participantX = new BigDecimal("72");
    @Column(name="participant_y", nullable=false) private BigDecimal participantY = new BigDecimal("360");
    @Column(name="participant_width", nullable=false) private BigDecimal participantWidth = new BigDecimal("698");
    @Column(name="participant_font_size", nullable=false) private BigDecimal participantFontSize = new BigDecimal("28");
    @Column(name="participant_min_font_size", nullable=false) private BigDecimal participantMinFontSize = new BigDecimal("14");
    @Enumerated(EnumType.STRING) @Column(name="participant_alignment", nullable=false, length=16) private CertificateTextAlignment participantAlignment = CertificateTextAlignment.CENTER;
    @Column(name="event_x", nullable=false) private BigDecimal eventX = new BigDecimal("72");
    @Column(name="event_y", nullable=false) private BigDecimal eventY = new BigDecimal("310");
    @Column(name="event_width", nullable=false) private BigDecimal eventWidth = new BigDecimal("698");
    @Column(name="event_font_size", nullable=false) private BigDecimal eventFontSize = new BigDecimal("14");
    @Column(name="event_min_font_size", nullable=false) private BigDecimal eventMinFontSize = new BigDecimal("9");
    @Column(name="event_line_spacing", nullable=false) private BigDecimal eventLineSpacing = new BigDecimal("18");
    @Enumerated(EnumType.STRING) @Column(name="event_alignment", nullable=false, length=16) private CertificateTextAlignment eventAlignment = CertificateTextAlignment.CENTER;
    @Column(name="dynamic_x", nullable=false) private BigDecimal dynamicX = new BigDecimal("150");
    @Column(name="dynamic_y", nullable=false) private BigDecimal dynamicY = new BigDecimal("235");
    @Column(name="dynamic_width", nullable=false) private BigDecimal dynamicWidth = new BigDecimal("540");
    @Column(name="dynamic_max_height", nullable=false) private BigDecimal dynamicMaxHeight = new BigDecimal("150");
    @Column(name="dynamic_font_size", nullable=false) private BigDecimal dynamicFontSize = new BigDecimal("13");
    @Column(name="dynamic_min_font_size", nullable=false) private BigDecimal dynamicMinFontSize = new BigDecimal("8");
    @Column(name="dynamic_line_spacing", nullable=false) private BigDecimal dynamicLineSpacing = new BigDecimal("18");
    @Enumerated(EnumType.STRING) @Column(name="dynamic_alignment", nullable=false, length=16) private CertificateTextAlignment dynamicAlignment = CertificateTextAlignment.LEFT;

    public Event getEvent() { return event; } public void setEvent(Event event) { this.event = event; }
    public String getDisplayName() { return displayName; } public void setDisplayName(String value) { displayName = value; }
    public boolean isActive() { return active; } public void setActive(boolean value) { active = value; }
    public int getDisplayOrder() { return displayOrder; } public void setDisplayOrder(int value) { displayOrder = value; }
    public String getOriginalFilename() { return originalFilename; } public void setOriginalFilename(String value) { originalFilename = value; }
    public EventDocumentStorageProvider getStorageProvider() { return storageProvider; } public void setStorageProvider(EventDocumentStorageProvider value) { storageProvider = value; }
    public String getStorageKey() { return storageKey; } public void setStorageKey(String value) { storageKey = value; }
    public String getContentType() { return contentType; } public void setContentType(String value) { contentType = value; }
    public long getSizeBytes() { return sizeBytes; } public void setSizeBytes(long value) { sizeBytes = value; }
    public String getSha256() { return sha256; } public void setSha256(String value) { sha256 = value; }
    public Instant getUploadedAt() { return uploadedAt; } public void setUploadedAt(Instant value) { uploadedAt = value; }
    public String getUploadedBy() { return uploadedBy; } public void setUploadedBy(String value) { uploadedBy = value; }
    public Set<Race> getRaces() { return races; } public void setRaces(Set<Race> value) { races = new LinkedHashSet<>(value); }
    public BigDecimal getParticipantX(){return participantX;} public void setParticipantX(BigDecimal v){participantX=v;}
    public BigDecimal getParticipantY(){return participantY;} public void setParticipantY(BigDecimal v){participantY=v;}
    public BigDecimal getParticipantWidth(){return participantWidth;} public void setParticipantWidth(BigDecimal v){participantWidth=v;}
    public BigDecimal getParticipantFontSize(){return participantFontSize;} public void setParticipantFontSize(BigDecimal v){participantFontSize=v;}
    public BigDecimal getParticipantMinFontSize(){return participantMinFontSize;} public void setParticipantMinFontSize(BigDecimal v){participantMinFontSize=v;}
    public CertificateTextAlignment getParticipantAlignment(){return participantAlignment;} public void setParticipantAlignment(CertificateTextAlignment v){participantAlignment=v;}
    public BigDecimal getEventX(){return eventX;} public void setEventX(BigDecimal v){eventX=v;}
    public BigDecimal getEventY(){return eventY;} public void setEventY(BigDecimal v){eventY=v;}
    public BigDecimal getEventWidth(){return eventWidth;} public void setEventWidth(BigDecimal v){eventWidth=v;}
    public BigDecimal getEventFontSize(){return eventFontSize;} public void setEventFontSize(BigDecimal v){eventFontSize=v;}
    public BigDecimal getEventMinFontSize(){return eventMinFontSize;} public void setEventMinFontSize(BigDecimal v){eventMinFontSize=v;}
    public BigDecimal getEventLineSpacing(){return eventLineSpacing;} public void setEventLineSpacing(BigDecimal v){eventLineSpacing=v;}
    public CertificateTextAlignment getEventAlignment(){return eventAlignment;} public void setEventAlignment(CertificateTextAlignment v){eventAlignment=v;}
    public BigDecimal getDynamicX(){return dynamicX;} public void setDynamicX(BigDecimal v){dynamicX=v;}
    public BigDecimal getDynamicY(){return dynamicY;} public void setDynamicY(BigDecimal v){dynamicY=v;}
    public BigDecimal getDynamicWidth(){return dynamicWidth;} public void setDynamicWidth(BigDecimal v){dynamicWidth=v;}
    public BigDecimal getDynamicMaxHeight(){return dynamicMaxHeight;} public void setDynamicMaxHeight(BigDecimal v){dynamicMaxHeight=v;}
    public BigDecimal getDynamicFontSize(){return dynamicFontSize;} public void setDynamicFontSize(BigDecimal v){dynamicFontSize=v;}
    public BigDecimal getDynamicMinFontSize(){return dynamicMinFontSize;} public void setDynamicMinFontSize(BigDecimal v){dynamicMinFontSize=v;}
    public BigDecimal getDynamicLineSpacing(){return dynamicLineSpacing;} public void setDynamicLineSpacing(BigDecimal v){dynamicLineSpacing=v;}
    public CertificateTextAlignment getDynamicAlignment(){return dynamicAlignment;} public void setDynamicAlignment(CertificateTextAlignment v){dynamicAlignment=v;}
}
