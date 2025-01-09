package com.startup.goHappy.controllers;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.ExecutionException;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.startup.goHappy.entities.model.Referral;
import com.startup.goHappy.entities.model.UserMemberships;
import com.startup.goHappy.entities.repository.ReferralRepository;
import com.startup.goHappy.entities.repository.UserMembershipsRepository;
import com.startup.goHappy.integrations.service.EmailService;
import com.startup.goHappy.utils.Constants;
import io.swagger.annotations.ApiOperation;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.alibaba.fastjson.JSONObject;
import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.CollectionReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Query;
import com.google.cloud.firestore.QuerySnapshot;
import com.startup.goHappy.entities.model.UserProfile;
import com.startup.goHappy.entities.repository.UserProfileRepository;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import javax.mail.MessagingException;

@RestController
@RequestMapping("auth")
public class AuthController {

    @Autowired
    UserProfileRepository userProfileService;
    @Autowired
    UserProfileController userProfileController;
    @Autowired
    MembershipController membershipController;

    private static final ObjectMapper objectMapper = new ObjectMapper();
    private final String GUPSHUP_BASE_URL = "https://enterprise.smsgupshup.com/GatewayAPI/rest";

    private RestTemplate restTemplate;

    @Value("${gupshup.userId}")
    private String userId;
    @Value("${gupshup.userPass}")
    private String userPass;

    public AuthController() {
        this.restTemplate = new RestTemplate();
    }

    @ApiOperation(value = "To register the user on the platform")
    @PostMapping("register")
    public void register(@RequestBody JSONObject userProfile, @RequestBody JSONObject referDetails) throws IOException, ExecutionException, InterruptedException {
        userProfileController.create(userProfile);
        Referral referObject = new Referral();
        referObject.setId(UUID.randomUUID().toString());
        CollectionReference userProfiles = userProfileService.getCollectionReference();
        Query query = null;
//		if(StringUtils.isEmpty(params.getString("email"))) {
        query = userProfiles.whereEqualTo("selfInviteCode", "" + referDetails.getString("referralId"));
        ApiFuture<QuerySnapshot> querySnapshot = query.get();
        UserProfile refereeUser = null;
        for (DocumentSnapshot document : querySnapshot.get().getDocuments()) {
            refereeUser = document.toObject(UserProfile.class);
            referObject.setFrom(refereeUser.getPhone());
            referObject.setReferralId(referDetails.getString("referralId"));

            referObject.setTo(userProfile.getString("phone"));
            referObject.setTime("" + new Date().getTime());
            referObject.setHasAttendedSession(false);

            userProfileController.refer(referObject);

            break;
        }
        return;
    }

    @ApiOperation(value = "To login the user and generate token")
    @PostMapping("login")
    public JSONObject login(@RequestBody JSONObject params) throws Exception {

        CollectionReference userProfiles = userProfileService.getCollectionReference();

        Query query = null;
        query = userProfiles.whereEqualTo("phone", "" + params.getString("phone"));

        ApiFuture<QuerySnapshot> querySnapshot = query.get();
        UserProfile user = null;
        JSONObject output = new JSONObject();
        for (DocumentSnapshot document : querySnapshot.get().getDocuments()) {
            user = document.toObject(UserProfile.class);
            break;
        }
        if (user != null) {
            if (!StringUtils.isEmpty(params.getString("fcmToken"))) {
                user.setFcmToken(params.getString("fcmToken"));
                userProfileService.save(user);
            }
            JSONObject getMembershipByPhoneParams = new JSONObject();
            getMembershipByPhoneParams.put("phone", user.getPhone());
            UserMemberships userMembership = membershipController.getMembershipByPhone(getMembershipByPhoneParams);
            if (userMembership == null) {
                userMembership = membershipController.createNewMembership(user.getPhone(), user.getId());
            }
            Map<String, Object> userMap = objectMapper.convertValue(user, Map.class);
            Map<String, Object> membershipMap = objectMapper.convertValue(userMembership, Map.class);

            // Merge the maps into one
            Map<String, Object> mergedMap = new HashMap<>(userMap);
            mergedMap.putAll(membershipMap);
            return new JSONObject(mergedMap);
        } else if (!StringUtils.isEmpty(params.getString("token"))) {
            Instant instance = java.time.Instant.ofEpochMilli(new Date().getTime());
            ZonedDateTime zonedDateTime = java.time.ZonedDateTime
                    .ofInstant(instance, java.time.ZoneId.of("Asia/Kolkata"));
            params.put("dateOfJoining", "" + zonedDateTime.toInstant().toEpochMilli());
            params.put("dateOfJoiningDateObject", "" + zonedDateTime.toLocalDateTime().toString());
            JSONObject referDetails = new JSONObject();
            referDetails.put("referralId", params.getString("referralId"));
            params.remove("referralId");
            register(params, referDetails);
            ApiFuture<QuerySnapshot> querySnapshot1 = query.get();
            UserProfile user1 = null;
            for (DocumentSnapshot document : querySnapshot1.get().getDocuments()) {
                user1 = document.toObject(UserProfile.class);
                break;
            }
            assert user1 != null;
            UserMemberships userMembership = membershipController.createNewMembership(user1.getPhone(), user1.getId());
            Map<String, Object> userMap = objectMapper.convertValue(user1, Map.class);
            Map<String, Object> membershipMap = objectMapper.convertValue(userMembership, Map.class);

            // Merge the maps into one
            Map<String, Object> mergedMap = new HashMap<>(userMap);
            mergedMap.putAll(membershipMap);
            return new JSONObject(mergedMap);
        }

        return null;
    }

    @ApiOperation(value = "Send OTP to user")
    @PostMapping("/init")
    public JSONObject init(@RequestBody JSONObject params) throws Exception {
        try {
            String messageTemplate = "Dear User,\n" +
                    "Your OTP for login to GoHappy Club is %code%. This code is valid for 30 minutes. Please do not share this OTP.\n" +
                    "Regards,\n" +
                    "GoHappy Club Team";

            UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(GUPSHUP_BASE_URL)
                    .queryParam("userid", userId)
                    .queryParam("password", userPass)
                    .queryParam("method", "TWO_FACTOR_AUTH")
                    .queryParam("v", "1.1")
                    .queryParam("phone_no", params.getString("phone"))
                    .queryParam("format", "text")
                    .queryParam("otpCodeLength", "6")
                    .queryParam("otpCodeType", "NUMERIC");

            String encodedMessage = messageTemplate
                    .replace("\n", "%0A")
                    .replace(" ", "%20")
                    .replace(",", "%2C");

            String finalUrl = builder.toUriString() + "&msg=" + encodedMessage;

            ResponseEntity<String> response = restTemplate.getForEntity(
                    finalUrl,
                    String.class
            );

            JSONObject result = new JSONObject();
            if(Objects.requireNonNull(response.getBody()).contains("success")){
                result.put("success", true);
            }else{
                result.put("success", false);
            }
            return result;

        } catch (Exception e) {
            System.err.println("Error sending OTP: " + e.getMessage());
            return new JSONObject() {{
                put("success", false);
            }};
        }
    }

    @ApiOperation(value = "Verify user's OTP")
    @PostMapping("/verify")
    public JSONObject verify(@RequestBody JSONObject params) throws Exception {
        try {
            UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(GUPSHUP_BASE_URL)
                    .queryParam("userid", userId)
                    .queryParam("password", userPass)
                    .queryParam("method", "TWO_FACTOR_AUTH")
                    .queryParam("v", "1.1")
                    .queryParam("phone_no", params.getString("phone"))
                    .queryParam("otp_code", params.getString("otp"));

            ResponseEntity<String> response = restTemplate.getForEntity(
                    builder.toUriString(),
                    String.class
            );
            if (Objects.requireNonNull(response.getBody()).contains("success")) {
                return new JSONObject() {{
                    put("success", true);
                }};
            }
            return new JSONObject() {{
                put("success", false);
            }};
        }catch (Exception e) {
            System.err.println("Error verifying OTP: " + e.getMessage());
            return new JSONObject() {{
                put("success", false);
            }};
        }

    }
}
