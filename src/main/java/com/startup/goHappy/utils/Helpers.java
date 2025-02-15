package com.startup.goHappy.utils;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Random;

@Service
public class Helpers {
    public boolean matches(String fullText, String searchText) {
        String fullTextLower = fullText.toLowerCase();
        String[] searchWords = searchText.toLowerCase().split("\\s+");

        for (String searchWord : searchWords) {
            String[] fullTextWords = fullTextLower.split("\\s+");
            boolean wordFound = false;

            for (String fullWord : fullTextWords) {
                fullWord = fullWord.replaceAll("[^a-zA-Z0-9]", "");
                String cleanSearchWord = searchWord.replaceAll("[^a-zA-Z0-9]", "");
                if (fullWord.startsWith(cleanSearchWord)) {
                    wordFound = true;
                    break;
                }
            }

            if (!wordFound) {
                return false;
            }
        }
        return true;
    }

    public String FormatMilliseconds(long milliseconds) {
        Instant instant = Instant.ofEpochMilli(milliseconds);
        ZonedDateTime zonedDateTime = instant.atZone(ZoneId.of("Asia/Kolkata"));
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        return zonedDateTime.format(formatter);
    }

    public static String generateCouponCode() {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        StringBuilder sb = new StringBuilder();
        Random random = new Random();

        for (int i = 0; i < 4; i++) {
            for (int j = 0; j < 4; j++) {
                int index = random.nextInt(chars.length());
                sb.append(chars.charAt(index));
            }
            if (i < 3) {
                sb.append("-");
            }
        }

        return sb.toString();
    }
}
