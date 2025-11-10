package github.vijay_papanaboina.cloud_storage_api.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;

public class TransformRequest {
    @Min(value = 1, message = "Width must be at least 1")
    private Integer width;

    @Min(value = 1, message = "Height must be at least 1")
    private Integer height;

    @Pattern(regexp = "^(fill|fit|scale|thumb|crop|limit|pad|lfill|limit_pad|fit_pad|auto|imagga_scale|imagga_crop)?$", message = "Invalid crop mode. Valid values: fill, fit, scale, thumb, crop, limit, pad, lfill, limit_pad, fit_pad, auto, imagga_scale, imagga_crop")
    private String crop;

    @Pattern(regexp = "^(auto|best|good|eco|low|(8[0-9]|9[0-9]|100))?$", message = "Invalid quality. Valid values: auto, best, good, eco, low, or numeric value 80-100")
    private String quality;

    @Pattern(regexp = "^(webp|jpg|jpeg|png|gif|bmp|tiff|ico|pdf|svg|mp4|webm|ogv|flv|mov|wmv|mp3|wav|ogg|aac|flac)?$", message = "Invalid format")
    private String format;

    // Constructors
    public TransformRequest() {
    }

    public TransformRequest(Integer width, Integer height, String crop, String quality, String format) {
        this.width = width;
        this.height = height;
        this.crop = crop;
        this.quality = quality;
        this.format = format;
    }

    // Getters and Setters
    public Integer getWidth() {
        return width;
    }

    public void setWidth(Integer width) {
        this.width = width;
    }

    public Integer getHeight() {
        return height;
    }

    public void setHeight(Integer height) {
        this.height = height;
    }

    public String getCrop() {
        return crop;
    }

    public void setCrop(String crop) {
        this.crop = crop;
    }

    public String getQuality() {
        return quality;
    }

    public void setQuality(String quality) {
        this.quality = quality;
    }

    public String getFormat() {
        return format;
    }

    public void setFormat(String format) {
        this.format = format;
    }
}
