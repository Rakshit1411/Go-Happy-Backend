package com.startup.goHappy.integrations.service;

import com.alibaba.fastjson.JSONObject;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.cloud.firestore.CollectionReference;
import com.startup.goHappy.controllers.EventController;
import com.startup.goHappy.entities.model.Event;
import com.startup.goHappy.entities.repository.EventRepository;
import com.startup.goHappy.integrations.model.ZoomParticipantsDTO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class ZoomParticipantInfo {

    @Autowired
    EventRepository eventService;

    @Autowired
    ZoomService zoomService;
    @Autowired
    private EventController eventController;

    @Scheduled(cron = "0 59 23 * * *", zone = "Asia/Kolkata")
    public void getParticipantInfo() {
        long now = new Date().getTime();
        long twentyFourHoursAgo = now - (24*60*60*1000);

        JSONObject params = new JSONObject();
        params.put("minDate", "" + twentyFourHoursAgo);
        params.put("maxDate", "" + now);

        List<Event> events = eventController.getEventsWithinDateRange(params);
        for (Event event : events) {
        String meetingNumber = extractMeetingNumber(event.getMeetingLink());
        List<ZoomParticipantsDTO.Participant> participants = zoomService.getPastMeetingParticipants(meetingNumber);
            storeAttendanceData(event.getId(), participants);
        }
    }

    private Map<String, String> processAttendanceData(List<ZoomParticipantsDTO.Participant> participants) {
        Map<String, String> attendanceData = new HashMap<>();
        for (ZoomParticipantsDTO.Participant participant : participants) {
            int durationMinutes = participant.getDuration() / 60;
            attendanceData.put(participant.getName(), String.valueOf(durationMinutes));
        }
        return attendanceData;
    }

    public String extractMeetingNumber(String zoomLink) {
        System.out.println("Zooom link ==>"+zoomLink);
        String regex = "j\\/(\\d+)";

        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(zoomLink);
        if (matcher.find()) {
            return matcher.group(1);
        } else {
            return null;
        }
    }

    public void storeAttendanceData(String eventId, List<ZoomParticipantsDTO.Participant> zoomData) {
        CollectionReference eventRef = eventService.getCollectionReference();
        Map<String, Object> updates = new HashMap<>();
        updates.put("zoomAnalytics", zoomData);
        updates.put("lastUpdated", com.google.cloud.Timestamp.now());
        eventRef.document(eventId).update(updates);
    }

}
