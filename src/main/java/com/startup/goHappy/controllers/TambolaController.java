package com.startup.goHappy.controllers;

import com.alibaba.fastjson.JSONObject;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.cloud.firestore.CollectionReference;
import com.startup.goHappy.entities.model.Event;
import com.startup.goHappy.entities.repository.EventRepository;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("tambola")
public class TambolaController {
    @Autowired
    EventRepository eventService;

    @ApiOperation(value = "get Tambola ticket by ticket number and event ID")
    @PostMapping("getTicket")
    public ResponseEntity<byte[]> getTambolaTicket(@RequestBody JSONObject params) throws IOException {
        ObjectMapper objectMapper = new ObjectMapper();
        Optional<Event> oevent = eventService.findById(params.getString("eventId"));
        Event event = oevent.get();
        List<String> tickets = event.getTambolaTickets();
        String ticket = tickets.get(Integer.parseInt(params.getString("ticketNumber")));
        JSONObject output = new JSONObject();
        output.put("ticket", ticket);
        if (ticket != null) {
            int[][] ticketArray = parseTicketString(ticket);
            BufferedImage image = createTicketImage(ticketArray);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(image, "png", baos);
            byte[] imageBytes = baos.toByteArray();

            return ResponseEntity
                    .ok()
                    .contentType(MediaType.IMAGE_PNG)
                    .body(imageBytes);
        }
        return ResponseEntity
                .ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"message\":\"Ticket not found\"}".getBytes());
    }

    @ApiOperation(value = "generate random number for tambola")
    @PostMapping("getcallNumber")
    public JSONObject getcallNumber(@RequestBody JSONObject params) throws IOException {
        CollectionReference eventRef = eventService.getCollectionReference();
        Optional<Event> oevent = eventService.findById(params.getString("eventId"));
        Event event = oevent.get();
        List<Integer> callingNumbers = event.getTambolaNumberCaller();
        Map<String,Integer> liveTambola = event.getLiveTambola();
        int index = liveTambola.get("index");
        int lastNumber = callingNumbers.get(index);
        int number = callingNumbers.get(index+1);
        liveTambola.replace("lastNumber",lastNumber);
        liveTambola.replace("value",number);
        liveTambola.replace("index",index+1);
        Map<String, Object> map = new HashMap<>();
        map.put("liveTambola",liveTambola);
        eventRef.document(params.getString("eventId")).update(map);
        JSONObject output = new JSONObject();
        output.put("number",lastNumber);
        return output;
    }

    @ApiOperation(value = "get List of already called numbers for tambola")
    @PostMapping("getCalledNumbers")
    public JSONObject getCalledNumbers(@RequestBody JSONObject params) throws IOException {
        Optional<Event> oevent = eventService.findById(params.getString("eventId"));
        Event event = oevent.get();
        List<Integer> callingNumbers = event.getTambolaNumberCaller();
        Map<String,Integer> liveTambola = event.getLiveTambola();
        List<Integer> alreadyCalledNumbers = callingNumbers.subList(0, liveTambola.get("index"));
        JSONObject output = new JSONObject();
        output.put("numbers",alreadyCalledNumbers);
        return output;
    }

    private int[][] parseTicketString(String ticketString) throws IOException {
        ticketString = ticketString.replaceAll("^\"|\"$", "");

        ObjectMapper objectMapper = new ObjectMapper();
        List<List<Integer>> ticketList = objectMapper.readValue(ticketString,
                new com.fasterxml.jackson.core.type.TypeReference<List<List<Integer>>>() {
                });

        int[][] ticket = new int[ticketList.size()][];
        for (int i = 0; i < ticketList.size(); i++) {
            List<Integer> row = ticketList.get(i);
            ticket[i] = row.stream().mapToInt(Integer::intValue).toArray();
        }

        return ticket;
    }

    public BufferedImage createTicketImage(int[][] ticket) {
        int imageWidth = 550;
        int imageHeight = 180;
        int cellWidth = 50;
        int cellHeight = 50;
        int gap = 5;

        int gridWidth = (9 * (cellWidth + gap)) - gap;
        int gridHeight = (3 * (cellHeight + gap)) - gap;

        int startX = (imageWidth - gridWidth) / 2;
        int startY = (imageHeight - gridHeight) / 2;

        BufferedImage image = new BufferedImage(imageWidth, imageHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = image.createGraphics();

        g2d.setColor(Color.getHSBColor(5, 49, 100));
        g2d.fillRect(0, 0, imageWidth, imageHeight);

        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        g2d.setColor(Color.WHITE);
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 9; j++) {
                int x = startX + j * (cellWidth + gap);
                int y = startY + i * (cellHeight + gap);
                g2d.fillRoundRect(x, y, cellWidth, cellHeight, 15, 15);
            }
        }

        g2d.setColor(Color.BLACK);
        g2d.setFont(new Font("Arial", Font.BOLD, 20));
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 9; j++) {
                if (ticket[i][j] != 0) {
                    String number = String.valueOf(ticket[i][j]);
                    int x = startX + j * (cellWidth + gap) + cellWidth / 2 - g2d.getFontMetrics().stringWidth(number) / 2;
                    int y = startY + i * (cellHeight + gap) + cellHeight / 2 + g2d.getFontMetrics().getAscent() / 2 - 5;
                    g2d.drawString(number, x, y);
                }
            }
        }

        g2d.dispose();
        return image;
    }
}
