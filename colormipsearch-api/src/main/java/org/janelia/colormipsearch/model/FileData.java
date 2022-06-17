package org.janelia.colormipsearch.model;

import java.nio.file.Paths;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.janelia.colormipsearch.model.json.FileDataDeserializer;
import org.janelia.colormipsearch.model.json.FileDataSerializer;

@JsonSerialize(using = FileDataSerializer.class)
@JsonDeserialize(using = FileDataDeserializer.class)
public class FileData {
    public enum FileDataType {
        file,
        zipEntry
    };

    public static FileData fromString(String fn) {
        if (StringUtils.isNotBlank(fn)) {
            FileData fd = new FileData();
            fd.setDataType(FileDataType.file);
            fd.setFileName(fn);
            return fd;
        } else {
            return null;
        }
    }

    public static FileData fromComponents(FileDataType fileDataType, String parent, String name) {
        if (fileDataType == FileDataType.zipEntry) {
            FileData fd = new FileData();
            fd.setDataType(FileDataType.zipEntry);
            fd.setFileName(parent);
            fd.setEntryName(name);
            return fd;
        } else {
            FileData fd = new FileData();
            fd.setDataType(FileDataType.file);
            fd.setFileName(Paths.get(parent).resolve(name).toString());
            return fd;
        }
    }

    private FileDataType dataType;
    private String fileName;
    private String entryName;

    public FileDataType getDataType() {
        return dataType;
    }

    public void setDataType(FileDataType dataType) {
        this.dataType = dataType;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getEntryName() {
        return entryName;
    }

    public void setEntryName(String entryName) {
        this.entryName = entryName;
    }

    @JsonIgnore
    public String getName() {
        return StringUtils.isNotBlank(entryName) ? entryName : fileName;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;

        if (o == null || getClass() != o.getClass()) return false;

        FileData fileData = (FileData) o;

        return new EqualsBuilder()
                .append(dataType, fileData.dataType)
                .append(fileName, fileData.fileName)
                .append(entryName, fileData.entryName)
                .isEquals();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder(17, 37)
                .append(dataType)
                .append(fileName)
                .append(entryName)
                .toHashCode();
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this)
                .append("dataType", dataType)
                .append("fileName", fileName)
                .append("entryName", entryName)
                .toString();
    }
}
