package ru.sportsresults.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import ru.sportsresults.importing.ImportFileType;

@Entity
@Table(name = "import_mapping_profiles")
public class ImportMappingProfile extends BaseEntity {

    @NotBlank
    @Size(max = 160)
    @Column(nullable = false, length = 160)
    private String name;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "file_type", nullable = false, length = 16)
    private ImportFileType fileType;

    @NotBlank
    @Size(min = 64, max = 64)
    @Column(name = "header_signature", nullable = false, length = 64)
    private String headerSignature;

    @NotNull
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "mapping_json", nullable = false, columnDefinition = "jsonb")
    private String mappingJson;

    @Size(max = 255)
    @Column(name = "race_discriminator_header", length = 255)
    private String raceDiscriminatorHeader;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public ImportFileType getFileType() { return fileType; }
    public void setFileType(ImportFileType fileType) { this.fileType = fileType; }
    public String getHeaderSignature() { return headerSignature; }
    public void setHeaderSignature(String headerSignature) { this.headerSignature = headerSignature; }
    public String getMappingJson() { return mappingJson; }
    public void setMappingJson(String mappingJson) { this.mappingJson = mappingJson; }
    public String getRaceDiscriminatorHeader() { return raceDiscriminatorHeader; }
    public void setRaceDiscriminatorHeader(String raceDiscriminatorHeader) {
        this.raceDiscriminatorHeader = raceDiscriminatorHeader;
    }
}
