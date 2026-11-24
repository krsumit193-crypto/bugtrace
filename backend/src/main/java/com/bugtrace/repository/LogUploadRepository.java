package com.bugtrace.repository;

import com.bugtrace.entity.LogUpload;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/**
 * Upload history queries.
 */
public interface LogUploadRepository extends JpaRepository<LogUpload, Long> {

    @Query("""
            select coalesce(sum(u.totalLines), 0) as totalLines,
                   coalesce(sum(u.parsedEntries), 0) as parsedEntries,
                   coalesce(sum(u.malformedEntries), 0) as malformedEntries,
                   count(u) as uploadCount
            from LogUpload u
            """)
    UploadTotals summaryTotals();

    interface UploadTotals {
        Long getTotalLines();

        Long getParsedEntries();

        Long getMalformedEntries();

        Long getUploadCount();
    }
}
