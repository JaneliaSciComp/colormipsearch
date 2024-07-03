package org.janelia.colormipsearch.model;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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
        return fromComponentsUsingParentPath(fileDataType, Paths.get(parent), name);
    }

    public static FileData fromComponentsWithCanonicPath(FileDataType fileDataType, String parent, String name) {
        return fromComponentsUsingParentPath(fileDataType, asCanonicPath(parent), name);
    }

    private static Path asCanonicPath(String p) {
        try {
            return Paths.get(p).toRealPath();
        } catch (IOException e)  {
            return Paths.get(p).toAbsolutePath().normalize();
        }
    }

    public static FileData fromComponentsUsingParentPath(FileDataType fileDataType, Path parentPath, String name) {
        if (fileDataType == FileDataType.zipEntry) {
            FileData fd = new FileData();
            fd.setDataType(FileDataType.zipEntry);
            fd.setFileName(parentPath.toString());
            fd.setEntryName(name);
            return fd;
        } else {
            FileData fd = new FileData();
            fd.setDataType(FileDataType.file);
            fd.setFileName(parentPath.resolve(name).toString());
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

    @JsonIgnore
    public String getNameCompOnly() {
        Path p = Paths.get(getName());
        return p.getFileName().toString();
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
