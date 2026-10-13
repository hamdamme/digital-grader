package com.digitalgrader.scanning;

import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;

class ScanWorkerTest {
    @Test void doesNotConsumeBeforeReadinessOrAfterFailedStartup() {
        var scanner=mock(SecurityScanner.class);var service=mock(ScanService.class);var jobs=mock(ScanRepository.class);
        var worker=new ScanWorker(scanner,service,jobs);
        worker.poll();verifyNoInteractions(service,jobs);
        doThrow(new IllegalStateException("missing runtime")).when(scanner).verifyReady();
        assertThatThrownBy(() -> worker.run(null)).hasMessage("missing runtime");
        worker.poll();verifyNoInteractions(service,jobs);
    }
    @Test void recoversClaimsAndScansOnlyAfterReadiness() {
        var scanner=mock(SecurityScanner.class);var service=mock(ScanService.class);var jobs=mock(ScanRepository.class);
        var worker=new ScanWorker(scanner,service,jobs);worker.run(null);worker.poll();
        var order=inOrder(scanner,jobs,service);order.verify(scanner).verifyReady();order.verify(jobs).recoverStale();order.verify(service).scanNext();
    }
}
