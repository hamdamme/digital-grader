package com.digitalgrader.scanning;

import java.nio.file.Path;
import java.util.List;

public interface SecurityScanner {
    void verifyReady();
    List<ScanReports.Finding> scan(Path archive);
}
