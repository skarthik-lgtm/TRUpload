package com.trupload.api.upload;

import java.util.List;

public record UploadRequest(List<UploadSelection> selections) {
}
