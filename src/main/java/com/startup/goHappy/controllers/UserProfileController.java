package com.startup.goHappy.controllers;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.security.GeneralSecurityException;
import java.util.*;
import java.util.concurrent.ExecutionException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.cloud.firestore.*;
import com.opencsv.CSVWriter;
import com.startup.goHappy.entities.model.PaymentLog;
import com.startup.goHappy.entities.model.Referral;
import com.startup.goHappy.entities.model.UserMemberships;
import com.startup.goHappy.entities.repository.PaymentLogRepository;
import com.startup.goHappy.entities.repository.ReferralRepository;
import com.startup.goHappy.enums.MembershipEnum;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;

import org.apache.commons.lang3.tuple.MutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.jetbrains.annotations.TestOnly;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import com.alibaba.fastjson.JSONObject;
import com.google.api.core.ApiFuture;
import com.startup.goHappy.config.Firestore.FirestoreConfig;
import com.startup.goHappy.entities.model.UserProfile;
import com.startup.goHappy.entities.repository.UserProfileRepository;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import javax.mail.MessagingException;
import javax.servlet.http.HttpServletResponse;

@RestController
@RequestMapping("user")
public class UserProfileController {

    @Autowired
    UserProfileRepository userProfileService;

    @Autowired
    PaymentLogRepository paymentLogService;

    @Autowired
    ReferralRepository referralService;


    @Autowired
    FirestoreConfig firestoreConfig;

    @Autowired
    EventController eventController;

    @Autowired
    MembershipController membershipController;


    public UserProfileController() {

    }
    private static final ObjectMapper objectMapper = new ObjectMapper();

    @TestOnly
    public UserProfileController(UserProfileRepository userProfileService) {
        this.userProfileService = userProfileService;
    }

    @PostMapping("create")
    public void create(@RequestBody JSONObject userProfile) {
        UserProfile up = new UserProfile();
        up.setId(UUID.randomUUID().toString());
        up.setAge(userProfile.getString("age"));
        up.setEmail(userProfile.getString("email"));
        up.setDateOfJoining(userProfile.getString("dateOfJoining"));
        up.setDateOfJoiningDateObject(userProfile.getString("dateOfJoiningDateObject"));
        up.setName(userProfile.getString("name"));
        up.setPhone("" + userProfile.getString("phone"));
        up.setProfileImage(userProfile.getString("profileImage"));
        up.setSessionsAttended("0");
        up.setPassword(userProfile.getString("password"));
        up.setGoogleSignIn(userProfile.getBoolean("googleSignIn"));
        up.setSource(userProfile.getString("source"));
        up.setFcmToken(userProfile.getString("fcmToken"));
        String selfInviteId = RandomStringUtils.random(6, "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789");
        up.setSelfInviteCode(selfInviteId);
        userProfileService.save(up);
        return;
    }

    @PostMapping("getUserByEmail")
    public JSONObject getUserByEmail(@RequestBody JSONObject params) throws IOException, InterruptedException, ExecutionException {
        CollectionReference userProfiles = userProfileService.getCollectionReference();

        Query query = userProfiles.whereEqualTo("email", params.getString("email"));

        ApiFuture<QuerySnapshot> querySnapshot = query.get();
        UserProfile user = null;
        for (DocumentSnapshot document : querySnapshot.get().getDocuments()) {
            user = document.toObject(UserProfile.class);
            break;
        }
//		List<UserProfile> result = IterableUtils.toList(user);
        JSONObject output = new JSONObject();
        output.put("user", user);
        return output;
    }

    @PostMapping("getUserByPhone")
    public JSONObject getUserByPhone(@RequestBody JSONObject params) throws IOException, InterruptedException, ExecutionException {
        CollectionReference userProfiles = userProfileService.getCollectionReference();

        Query query = userProfiles.whereEqualTo("phone", params.getString("phoneNumber"));

        ApiFuture<QuerySnapshot> querySnapshot = query.get();
        UserProfile user = null;
        for (DocumentSnapshot document : querySnapshot.get().getDocuments()) {
            user = document.toObject(UserProfile.class);
            break;
        }

        JSONObject output = new JSONObject();
        output.put("user", user);
        return output;
    }

    @GetMapping("/download")
    public StreamingResponseBody downloadCsv(@RequestParam String dateOfJoining, HttpServletResponse response) throws IOException, ExecutionException, InterruptedException {
        response.setContentType("text/csv");
        response.setHeader("Content-Disposition", "attachment; filename=\"users.csv\"");

        CollectionReference userProfiles = userProfileService.getCollectionReference();

        Query query = userProfiles.whereGreaterThanOrEqualTo("dateOfJoining", dateOfJoining);

        ApiFuture<QuerySnapshot> querySnapshot = query.get();
        List<String[]> users = new ArrayList();
        String[] columnNames = {"ID", "Name", "Age", "Email", "Phone Number",
                "Last Payment Date", "Invite Code", "Sessions Attended",
                "Date Of Joining", "Date of Joining (Date Format)",
                "Profile Image", "Last Payment Amount"};
        users.add(columnNames);
        for (DocumentSnapshot document : querySnapshot.get().getDocuments()) {
            UserProfile user = document.toObject(UserProfile.class);
            if (user == null) {
                continue;
            }
            String[] userData = {
                    user.getId(),
                    user.getName(),
                    user.getAge(),
                    user.getEmail(),
                    user.getPhone(),
                    user.getLastPaymentDate(),
                    user.getSelfInviteCode(),
                    user.getSessionsAttended(),
                    user.getDateOfJoining(),
                    user.getDateOfJoiningDateObject(),
                    user.getProfileImage(),
                    user.getLastPaymentAmount() != null ? user.getLastPaymentAmount().toString() : "",
                    user.getCity(),
                    user.getEmergencyContact(),

            };
            users.add(userData);
        }
        CSVWriter csvWriter = new CSVWriter(new OutputStreamWriter(response.getOutputStream()));
        csvWriter.writeAll(users);
        csvWriter.close();

        return outputStream -> outputStream.flush();
    }

    /*
    * This is used in @PaytringController.java to add the payment amount and date to user profile, not used anywhere else
    * */
    @PostMapping("setPaymentData")
    public void setPaymentData(@RequestBody JSONObject params) throws IOException, InterruptedException, ExecutionException {
        /*
        * @params
        * phoneNumber : String
        * amount : String
        * */
        CollectionReference userProfiles = userProfileService.getCollectionReference();

        Query profileQuery = userProfiles.whereEqualTo("phone", params.getString("phoneNumber"));

        ApiFuture<QuerySnapshot> querySnapshot1 = profileQuery.get();
        UserProfile user = null;
        for (DocumentSnapshot document : querySnapshot1.get().getDocuments()) {
            user = document.toObject(UserProfile.class);
            assert user != null;
            user.setLastPaymentAmount(Integer.parseInt(params.getString("amount")));
            user.setLastPaymentDate("" + new Date().getTime());
            break;
        }
        userProfileService.save(user);

    }
     /*
     * This is used for phonePe's callback, when the payment is in terminated state, then this one is called, this is not used with paytring's implementation, can be removed once we are fully moved out of phone pe
     * */
    @PostMapping("setPaymentDataContribution")
    public void setPaymentDataContribution(@RequestParam String phoneNumber, @RequestBody JSONObject params) throws IOException, InterruptedException, ExecutionException {
        CollectionReference userProfiles = userProfileService.getCollectionReference();
        /*
        * The below code checks for the payment status, whether success or false and proceeds accordingly
        * */
        String encodedResponse = params.getString("response");

        byte[] decodedBytes = Base64.getDecoder().decode(encodedResponse);
        String decodedString = new String(decodedBytes);
        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode decodedJson = objectMapper.readTree(decodedString);

        JsonNode dataNode = decodedJson.get("data");
        String merchantTransactionId = dataNode.get("merchantTransactionId").asText();
        int amountInPaisa = dataNode.get("amount").asInt();
        int amount = amountInPaisa / 100;
        String code = decodedJson.get("code").asText();
        if (!"PAYMENT_SUCCESS".equals(code)) return;
        Query profileQuery = userProfiles.whereEqualTo("phone", phoneNumber);

        ApiFuture<QuerySnapshot> querySnapshot1 = profileQuery.get();
        UserProfile user = null;
        for (DocumentSnapshot document : querySnapshot1.get().getDocuments()) {
            user = document.toObject(UserProfile.class);
            assert user != null;
            user.setLastPaymentAmount(Integer.parseInt(params.getString("amount")));
            user.setLastPaymentDate("" + new Date().getTime());
            break;
        }
        userProfileService.save(user);

    }

    @PostMapping("setPaymentDataWorkshop")
    public void setPaymentDataWorkshop(@RequestParam String phoneNumber, @RequestParam String orderId, @RequestParam String tambolaTicket, @RequestBody JSONObject params) throws IOException, InterruptedException, ExecutionException, MessagingException, GeneralSecurityException {
        String encodedResponse = params.getString("response");

        byte[] decodedBytes = Base64.getDecoder().decode(encodedResponse);
        String decodedString = new String(decodedBytes);

        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode decodedJson = objectMapper.readTree(decodedString);
        String code = decodedJson.get("code").asText();
        if (!"PAYMENT_SUCCESS".equals(code)) return;

        JSONObject bookEventParams = new JSONObject();
        bookEventParams.put("id",orderId);
        bookEventParams.put("phoneNumber",phoneNumber);
        bookEventParams.put("tambolaTicket",tambolaTicket);

        eventController.bookEvent(bookEventParams);

    }

    @PostMapping("update")
    public JSONObject updateUser(@RequestBody JSONObject params) throws IOException, InterruptedException, ExecutionException {

        CollectionReference userProfiles = userProfileService.getCollectionReference();
        if(!StringUtils.isEmpty(params.getString("phone"))) {

        if (params.getString("phone").startsWith("+")) {
                params.put("phone", params.getString("phone").substring(1));
            }
        }
        Query query = userProfiles.whereEqualTo("phone", params.getString("phone"));

        ApiFuture<QuerySnapshot> querySnapshot = query.get();
        UserProfile user = null;
        for (DocumentSnapshot document : querySnapshot.get().getDocuments()) {
            user = document.toObject(UserProfile.class);
            if (!StringUtils.isEmpty(params.getString("name")))
                user.setName(params.getString("name"));
            if (!StringUtils.isEmpty(params.getString("email")))
                user.setEmail(params.getString("email"));
            if (!StringUtils.isEmpty(params.getString("phone")))
                user.setPhone("" + params.getLong("phone"));
            if (!StringUtils.isEmpty(params.getString("city")))
                user.setCity("" + params.getString("city"));
            if (!StringUtils.isEmpty(params.getString("emergencyContact")))
                user.setEmergencyContact("" + params.getLong("emergencyContact"));
            if (!StringUtils.isEmpty(params.getString("age")))
                user.setAge("" + params.getString("age"));
            if (!StringUtils.isEmpty(params.getString("fcmToken")))
                user.setFcmToken(params.getString("fcmToken"));
            if (!StringUtils.isEmpty(params.getString("dob")))
                user.setDob(params.getString("dob"));
            if (!StringUtils.isEmpty(params.getString("sessionsAttended")))
                user.setSessionsAttended(params.getString("sessionsAttended"));
            break;
        }
        userProfileService.save(user);

        //retrieve userMembership Profile to send to frontend
        JSONObject getMembershipByPhoneParams = new JSONObject();
        getMembershipByPhoneParams.put("phone",params.getString("phone"));
        UserMemberships userMembership = membershipController.getMembershipByPhone(getMembershipByPhoneParams);
        if(userMembership == null){
            userMembership = membershipController.createNewMembership(user.getPhone(),user.getId());
        }
        Map<String, Object> userMap = objectMapper.convertValue(user, Map.class);
        Map<String, Object> membershipMap = objectMapper.convertValue(userMembership, Map.class);

        // Merge the maps into one
        Map<String, Object> mergedMap = new HashMap<>(userMap);
        mergedMap.putAll(membershipMap);
        return new JSONObject(mergedMap);
    }

    @PostMapping("updateProfileImage")
    public UserProfile updateProfileImage(@RequestBody JSONObject params) throws IOException, InterruptedException, ExecutionException {

        CollectionReference userProfiles = userProfileService.getCollectionReference();
        if (params.getString("phoneNumber").startsWith("+")) {
            params.put("phone", params.getString("phoneNumber").substring(1));
        }
        Query query = userProfiles.whereEqualTo("phone", params.getString("phoneNumber"));

        ApiFuture<QuerySnapshot> querySnapshot = query.get();
        UserProfile user = null;
        for (DocumentSnapshot document : querySnapshot.get().getDocuments()) {
            user = document.toObject(UserProfile.class);
            user.setProfileImage(params.getString("profileImage"));
            break;
        }
        userProfileService.save(user);
        return user;
    }

    @PostMapping("refer")
    public void refer(@RequestBody Referral referObject) throws IOException, InterruptedException, ExecutionException {
        CollectionReference referrals = referralService.getCollectionReference();
        Query query = referrals.whereEqualTo("to", referObject.getTo());

        ApiFuture<QuerySnapshot> querySnapshot = query.get();
        UserProfile user = null;
        if (querySnapshot.get().getDocuments().size() == 0) {
            referralService.save(referObject);
        }
    }

    @PostMapping("sessionAttended")
    public void sessionAttended(@RequestBody JSONObject userDetails) throws ExecutionException, InterruptedException {
        CollectionReference referrals = referralService.getCollectionReference();
        Query query = referrals.whereEqualTo("to", userDetails.getString("phone"));
        ApiFuture<QuerySnapshot> querySnapshot = query.get();
        Referral referral = null;
        if (querySnapshot.get().getDocuments().size() != 0) {
            for (DocumentSnapshot document : querySnapshot.get().getDocuments()) {
                referral = document.toObject(Referral.class);
                referral.setHasAttendedSession(true);
                referralService.save(referral);
                break;
            }
        }
    }

    @PostMapping("referralsList")
    public JSONObject referralsList(@RequestBody JSONObject params) throws ExecutionException, InterruptedException {
        CollectionReference referrals = referralService.getCollectionReference();
        Query query = referrals.whereEqualTo("from", params.getString("from"));
        if (!StringUtils.isEmpty(params.getString("hasAttendedSession"))) {
            query = query.whereEqualTo("hasAttendedSession", params.getBoolean("hasAttendedSession"));
        }
//		Query query = referrals.whereEqualTo("from", "919427876625");
        List<Referral> referralsList = new ArrayList<>();
        ApiFuture<QuerySnapshot> querySnapshot = query.get();
        List<String> userIds = new ArrayList<>();
        List<QueryDocumentSnapshot> referralsSnapshot = querySnapshot.get().getDocuments();
        if (referralsSnapshot.size() != 0) {
            for (DocumentSnapshot document : referralsSnapshot) {
                Referral obj = document.toObject(Referral.class);
                userIds.add(obj.getTo());
                referralsList.add(obj);
            }
        }

        CollectionReference userProfiles = userProfileService.getCollectionReference();
        Query userQuery = userProfiles.whereIn("phone", userIds);
        ApiFuture<QuerySnapshot> userQuerySnapshot = userQuery.get();
        List<QueryDocumentSnapshot> usersSnapshot = userQuerySnapshot.get().getDocuments();
        for (Referral ref : referralsList) {
            try {
                for (DocumentSnapshot userDocument : usersSnapshot) {
                    UserProfile user = userDocument.toObject(UserProfile.class);
                    if (StringUtils.equals(ref.getTo(), user.getPhone()) == true) {
                        ref.setToName(user.getName());
                        ref.setToProfileImage(user.getProfileImage());
                        break;
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        referralsList.sort(new Comparator<Referral>() {
            @Override
            public int compare(Referral m1, Referral m2) {
                if (m1.getTime() == m2.getTime()) {
                    return 0;
                }
                return Long.parseLong(m1.getTime()) > Long.parseLong(m2.getTime()) ? -1 : 1;
            }
        });

        JSONObject output = new JSONObject();
        output.put("referrals", referralsList);
        return output;
    }

    @PostMapping("topReferrals")
    public JSONObject topReferrals(@RequestBody JSONObject params) throws ExecutionException, InterruptedException {
        CollectionReference referrals = referralService.getCollectionReference();
        Integer top = params.getInteger("top");
        Query query = referrals.whereGreaterThanOrEqualTo("time", params.getString("time"));
        if (!StringUtils.isEmpty(params.getString("hasAttendedSession"))) {
            query = query.whereEqualTo("hasAttendedSession", params.getBoolean("hasAttendedSession"));
        }
        HashMap<String, Integer> ranking = new HashMap<>();
        List<Referral> referralsList = new ArrayList<>();
        ApiFuture<QuerySnapshot> querySnapshot = query.get();
        if (querySnapshot.get().getDocuments().size() != 0) {
            for (DocumentSnapshot document : querySnapshot.get().getDocuments()) {
                Referral referral = document.toObject(Referral.class);
                if (ranking.containsKey(referral.getFrom()))
                    ranking.put(referral.getFrom(), ranking.get(referral.getFrom()) + 1);
                else
                    ranking.put(referral.getFrom(), 1);
                referralsList.add(document.toObject(Referral.class));
            }
        }

        List<Pair<String, Integer>> sorted = new ArrayList<>();
        for (String key : ranking.keySet()) {
            Pair<String, Integer> pair = new MutablePair<>(key, ranking.get(key));
            sorted.add(pair);
        }
        sorted.sort(new Comparator<Pair<String, Integer>>() {
            @Override
            public int compare(Pair<String, Integer> m1, Pair<String, Integer> m2) {
                if (m1.getRight() == m2.getRight()) {
                    return 0;
                }
                return m1.getRight() > m2.getRight() ? -1 : 1;
            }
        });
        sorted = sorted.subList(0, top >= sorted.size() ? sorted.size() : top);

        JSONObject output = new JSONObject();
        output.put("referrals", sorted);
        return output;
    }
}
