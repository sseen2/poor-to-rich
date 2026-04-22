package com.poortorich.report.repository;

import com.poortorich.chat.entity.ChatParticipant;
import com.poortorich.chat.entity.Chatroom;
import com.poortorich.report.entity.Report;
import com.poortorich.report.entity.enums.ReportReason;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ReportRepository extends JpaRepository<Report, Long> {

    boolean existsByReporterAndReportedAndChatroomAndReason(
            ChatParticipant reporterMember,
            ChatParticipant reportedMember,
            Chatroom chatroom,
            ReportReason reportReason
    );

    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM Report r WHERE r.chatroom = :chatroom")
    void deleteByChatroom(@Param("chatroom") Chatroom chatroom);
}
