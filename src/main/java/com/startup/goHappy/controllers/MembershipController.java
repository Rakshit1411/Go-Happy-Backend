package com.startup.goHappy.controllers;


import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.*;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.startup.goHappy.entities.model.*;
import com.startup.goHappy.entities.repository.*;
import com.startup.goHappy.enums.MembershipEnum;
import com.startup.goHappy.enums.TransactionTypeEnum;
import com.startup.goHappy.enums.VoucherStatusEnum;
import com.startup.goHappy.integrations.service.EmailService;
import com.startup.goHappy.utils.Constants;
import com.startup.goHappy.utils.Helpers;
import io.swagger.annotations.ApiOperation;
import org.apache.commons.collections4.IterableUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.mail.MessagingException;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static com.startup.goHappy.utils.Helpers.generateCouponCode;

@RestController
@RequestMapping("/membership")
public class MembershipController {
    @Autowired
    UserProfileRepository userProfileService;

    @Autowired
    UserProfileController userProfileController;

    @Autowired
    PaymentLogRepository paymentLogService;

    @Autowired
    MembershipRepository membershipService;

    @Autowired
    UserMembershipsRepository userMembershipsService;

    @Autowired
    CoinPackagesRepository coinPackagesService;

    @Autowired
    CoinTransactionsRepository coinTransactionsService;

    @Autowired
    VouchersRepository vouchersService;

    @Autowired
    UserVouchersRepository userVouchersService;

    @Autowired
    Constants constants;

    @Autowired
    Helpers helpers;

    @Autowired
    EmailService emailService;

    private final String subject = "GoHappy Club Family - Membership";

    @ApiOperation(value = "To fetch all memberships in subscription plan screen")
    @GetMapping("/listAll")
    public List<Membership> listAll() {
        Iterable<Membership> memberships = membershipService.retrieveAll();
        return StreamSupport.stream(memberships.spliterator(), false)
                .filter(Membership::getActive)
                .collect(Collectors.toList());
    }

    @ApiOperation(value = "To get all the coin pricing and packages")
    @GetMapping("/listCoinPackages")
    public List<CoinPackages> listCoinPackages() {
        Iterable<CoinPackages> packages = coinPackagesService.retrieveAll();
        return IterableUtils.toList(packages);
    }

    @ApiOperation(value = "activate a user's free trial for 1 month/ will be used as a callback for PhonePe")
    @PostMapping("/activateFreeTrial")
    public UserMemberships activateFreeTrial(@RequestBody JSONObject params) throws ExecutionException, InterruptedException {
        UserMemberships userMembership = getMembershipByPhone(params);

        Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("Asia/Kolkata"));
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);

        userMembership.setMembershipStartDate(String.valueOf(calendar.getTimeInMillis()));
        calendar.add(Calendar.MONTH, 1);
        userMembership.setMembershipEndDate(String.valueOf(calendar.getTimeInMillis()));
        userMembership.setFreeTrialActive(true);
        userMembership.setMembershipType(MembershipEnum.Silver);

        userMembershipsService.save(userMembership);
        return userMembership;
    }

    @ApiOperation("cancel or de-activate a user's free trial")
    @PostMapping("/cancelFreeTrial")
    public void cancelFreeTrial(@RequestBody JSONObject params) throws ExecutionException, InterruptedException {
        UserMemberships userMembership = getMembershipByPhone(params);

        userMembership.setMembershipStartDate(null);
        userMembership.setMembershipType(MembershipEnum.Free);
        userMembership.setMembershipEndDate(null);
        userMembership.setFreeTrialActive(false);
        userMembership.setFreeTrialUsed(true);
        userMembershipsService.save(userMembership);
    }

    @ApiOperation(value = "When user completes payment for a subscription plan")
    @PostMapping("/buy")
    public void buySubscription(@RequestBody JSONObject params, @RequestParam String phoneNumber, @RequestParam String amount, @RequestParam String membershipId) throws ExecutionException, InterruptedException, IOException, MessagingException, GeneralSecurityException, FirebaseMessagingException {
        /*
        This API is callback for Phone Pe's successful/failed state update
        Expects :-
            phoneNumber
            amount
            membershipId
         */
//        String encodedResponse = params.getString("response");
//
//        byte[] decodedBytes = Base64.getDecoder().decode(encodedResponse);
//        String decodedString = new String(decodedBytes);
//
//        ObjectMapper objectMapper = new ObjectMapper();
//        JsonNode decodedJson = objectMapper.readTree(decodedString);
//        String code = decodedJson.get("code").asText();
//
//        // if status is error, then return
//        if ("PAYMENT_ERROR".equals(code)) return;

        Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("Asia/Kolkata"));
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);

        CollectionReference userProfiles = userProfileService.getCollectionReference();
        CollectionReference membershipsRef = membershipService.getCollectionReference();
        CollectionReference userMemberships = userMembershipsService.getCollectionReference();

        Query profileQuery = userProfiles.whereEqualTo("phone", phoneNumber);
        Query membershipQuery = membershipsRef.whereEqualTo("id", membershipId);
        Query userMembershipQuery = userMemberships.whereEqualTo("phone", phoneNumber);

        ApiFuture<QuerySnapshot> querySnapshot1 = profileQuery.get();
        ApiFuture<QuerySnapshot> querySnapshot2 = membershipQuery.get();
        ApiFuture<QuerySnapshot> querySnapshot3 = userMembershipQuery.get();

        Membership membership = null;
        for (DocumentSnapshot document : querySnapshot2.get().getDocuments()) {
            membership = document.toObject(Membership.class);
            break;
        }

        UserMemberships userMembership = null;
        for (DocumentSnapshot document : querySnapshot3.get().getDocuments()) {
            userMembership = document.toObject(UserMemberships.class);
            break;
        }

        UserProfile user = null;
        PaymentLog plog = new PaymentLog();
        CoinTransactions transaction = new CoinTransactions();
        for (DocumentSnapshot document : querySnapshot1.get().getDocuments()) {
            user = document.toObject(UserProfile.class);
            assert user != null;
            assert userMembership != null;
            assert membership != null;

            user.setLastPaymentAmount(Integer.valueOf(amount));
            user.setLastPaymentDate("" + new Date().getTime());

            //set user's membership type and start/end date & append coins in user's wallet
            userMembership.setFreeTrialUsed(true);
            userMembership.setFreeTrialActive(false);
            userMembership.setMembershipType(membership.getMembershipType());
            userMembership.setMembershipStartDate("" + calendar.getTimeInMillis());
            userMembership.setLastCoinsCreditedDate("" + calendar.getTimeInMillis());
            calendar.add(Calendar.MONTH, membership.getDuration());
            userMembership.setMembershipEndDate("" + calendar.getTimeInMillis());
            userMembership.setCoins(userMembership.getCoins() + membership.getCoinsPerMonth());

            // add this transaction to user's history
            transaction.setAmount(membership.getCoinsPerMonth());
            transaction.setSource("membership");
            transaction.setType(TransactionTypeEnum.CREDIT);
            transaction.setTransactionDate(new Date().getTime());
            transaction.setPhone(phoneNumber);
            transaction.setId(UUID.randomUUID().toString());
            transaction.setSourceId(membershipId);
            transaction.setTitle("Buy " + membership.getMembershipType() + " Membership");


            // set user's phone and uid in this membership document
            userMembership.setUserId(user.getId());
            userMembership.setPhone(user.getPhone());

//            plog.setPaymentDate(user.getLastPaymentDate());
//            plog.setPhone(user.getPhone());
//            plog.setId(UUID.randomUUID().toString());
//            plog.setAmount(user.getLastPaymentAmount());
//            plog.setType("membership");
            break;
        }

        List<String> voucherIds = membership.getVouchers();
        for (String voucherId : voucherIds) {
            UserVouchers userVoucher = new UserVouchers();
            userVoucher.setId(UUID.randomUUID().toString());
            userVoucher.setVoucherId(voucherId);
            userVoucher.setCode(generateCouponCode());
            userVoucher.setPhone(user.getPhone());
            userVoucher.setExpiryDate(Long.parseLong(userMembership.getMembershipEndDate()));
            userVoucher.setStatus(VoucherStatusEnum.ACTIVE);
            userVouchersService.save(userVoucher);
        }

        userProfileService.save(user);
        userMembershipsService.save(userMembership);
//        paymentLogService.save(plog);
        coinTransactionsService.save(transaction);

        //send email to user
        String currentContent = constants.getJoiningContent();
        if (user != null) {
            currentContent = currentContent.replace("${name}", user.getName());
            currentContent = currentContent.replace("${name}", user.getName());
            if (!StringUtils.isEmpty(user.getEmail()))
                emailService.sendSimpleMessage(user.getEmail(), subject, currentContent);
        }

        // send data through fcm to the frontend for redux update
        String MEMBERSHIP_TOPIC = "subscriptionUpdate";
        sendSubscriptionUpdateWithFcm(user.getFcmToken(), MEMBERSHIP_TOPIC, userMembership);
    }

    @ApiOperation(value = "to cancel a user's subscription")
    @PostMapping("/cancel")
    public UserMemberships cancelSubscription(@RequestBody JSONObject params) throws ExecutionException, InterruptedException, MessagingException, GeneralSecurityException, IOException {
        /*
        Expects :-
            phoneNumber
            reason
         */
        CollectionReference userMemberships = userMembershipsService.getCollectionReference();
        CollectionReference userProfiles = userProfileService.getCollectionReference();
        CollectionReference userVoucherRef = userVouchersService.getCollectionReference();

        String phoneNumber = params.getString("phoneNumber");
        Query memberQuery = userMemberships.whereEqualTo("phone", phoneNumber);
        Query profileQuery = userProfiles.whereEqualTo("phone", phoneNumber);

        ApiFuture<QuerySnapshot> querySnapshot1 = memberQuery.get();
        ApiFuture<QuerySnapshot> querySnapshot2 = profileQuery.get();

        // get the userMembership document corresponding to phoneNumber
        UserMemberships userMember = null;
        for (DocumentSnapshot document : querySnapshot1.get().getDocuments()) {
            userMember = document.toObject(UserMemberships.class);

            //set userMember's membership to free and dates to null
            assert userMember != null;
            userMember.setMembershipType(MembershipEnum.Free);
            userMember.setMembershipStartDate(null);
            userMember.setMembershipEndDate(null);
            userMember.setLastCoinsCreditedDate(null);
            userMember.setCancellationDate(""+new Date().getTime());
            userMember.setCancellationReason(params.getString("reason"));
            break;
        }
        userMembershipsService.save(userMember);

        // get the user's profile(full)
        UserProfile user = null;
        for (DocumentSnapshot document : querySnapshot2.get().getDocuments()) {
            user = document.toObject(UserProfile.class);
            break;
        }

        //send email to user
        String currentContent = constants.getCancelContent();
        if (user != null) {
            currentContent = currentContent.replace("${name}", user.getName());
            currentContent = currentContent.replace("${name}", user.getName());
            if (!StringUtils.isEmpty(user.getEmail()))
                emailService.sendSimpleMessage(user.getEmail(), subject, currentContent);
        }

        Query userVouchersQuery = userVoucherRef.whereEqualTo("phone", phoneNumber).whereEqualTo("status", VoucherStatusEnum.ACTIVE);
        ApiFuture<QuerySnapshot> querySnapshot3 = userVouchersQuery.get();
        for (DocumentSnapshot document : querySnapshot3.get().getDocuments()) {
            UserVouchers userVoucher = document.toObject(UserVouchers.class);
            assert userVoucher != null;
            userVoucher.setStatus(VoucherStatusEnum.EXPIRED);
            userVoucher.setExpiryDate(new Date().getTime());
            userVouchersService.save(userVoucher);
        }

        return userMember;
    }

    @ApiOperation(value = "to renew a user's subscription")
    @PostMapping("/renew")
    public void renewSubscription(@RequestBody JSONObject params, @RequestParam String phoneNumber, @RequestParam String amount, @RequestParam String membershipId) throws ExecutionException, InterruptedException, FirebaseMessagingException, JsonProcessingException {
//        String encodedResponse = params.getString("response");
//
//        byte[] decodedBytes = Base64.getDecoder().decode(encodedResponse);
//        String decodedString = new String(decodedBytes);
//
//        ObjectMapper objectMapper = new ObjectMapper();
//        JsonNode decodedJson = objectMapper.readTree(decodedString);
//        String code = decodedJson.get("code").asText();
//
//        //check the status of the payment, if it is error, return
//        if ("PAYMENT_ERROR".equals(code)) return;

        Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("Asia/Kolkata"));
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);

        CollectionReference userProfiles = userProfileService.getCollectionReference();
        CollectionReference userMemberships = userMembershipsService.getCollectionReference();
        CollectionReference membershipRef = membershipService.getCollectionReference();

        Query memberQuery = userMemberships.whereEqualTo("phone", phoneNumber);
        Query userQuery = userProfiles.whereEqualTo("phone", phoneNumber);
        Query membershipQuery = membershipRef.whereEqualTo("id", membershipId);

        ApiFuture<QuerySnapshot> querySnapshot1 = memberQuery.get();
        ApiFuture<QuerySnapshot> querySnapshot2 = userQuery.get();
        ApiFuture<QuerySnapshot> querySnapshot3 = membershipQuery.get();

        Membership membership = null;
        for (DocumentSnapshot document : querySnapshot3.get().getDocuments()) {
            membership = document.toObject(Membership.class);
            break;
        }

        UserMemberships userMember = null;
        for (DocumentSnapshot document : querySnapshot1.get().getDocuments()) {
            userMember = document.toObject(UserMemberships.class);

            //change userMember's start/end date
            assert userMember != null;
            assert membership != null;
            userMember.setMembershipStartDate("" + calendar.getTimeInMillis());
            long timeDuration = Long.parseLong(userMember.getMembershipEndDate());
            calendar.setTimeInMillis(timeDuration);
            calendar.add(Calendar.MONTH, membership.getDuration());
            userMember.setMembershipEndDate("" + calendar.getTimeInMillis());
            break;
        }

        UserProfile user = null;
        for (DocumentSnapshot document : querySnapshot2.get().getDocuments()) {
            user = document.toObject(UserProfile.class);
            user.setLastPaymentDate("" + new Date().getTime());
            user.setLastPaymentAmount(Integer.valueOf(amount));

//            PaymentLog plog = new PaymentLog();
//            plog.setPaymentDate(user.getLastPaymentDate());
//            plog.setPhone(user.getPhone());
//            plog.setId(UUID.randomUUID().toString());
//            plog.setAmount(user.getLastPaymentAmount());
//            plog.setType("membership");
//            paymentLogService.save(plog);
            break;
        }

        List<String> voucherIds = membership.getVouchers();
        for (String voucherId : voucherIds) {
            UserVouchers userVoucher = new UserVouchers();
            userVoucher.setId(UUID.randomUUID().toString());
            userVoucher.setVoucherId(voucherId);
            userVoucher.setCode(generateCouponCode());
            userVoucher.setPhone(user.getPhone());
            userVoucher.setExpiryDate(Long.parseLong(userMember.getMembershipEndDate()));
            userVoucher.setStatus(VoucherStatusEnum.ACTIVE);
            userVouchersService.save(userVoucher);
        }

        userMembershipsService.save(userMember);
        userProfileService.save(user);

        // send fcm notification to the frontend for redux update
        String MEMBERSHIP_TOPIC = "subscriptionUpdate";
        sendSubscriptionUpdateWithFcm(user.getFcmToken(), MEMBERSHIP_TOPIC, userMember);

    }

    @ApiOperation(value = "to renew a user's subscription")
    @PostMapping("/upgrade")
    public void upgradeSubscription(@RequestBody JSONObject params, @RequestParam String phoneNumber, @RequestParam String amount, @RequestParam String membershipId) throws ExecutionException, InterruptedException, FirebaseMessagingException, JsonProcessingException {

        /*
         * Expects
         *   phoneNumber
         *   membershipId
         *   amount
         * */

//        String encodedResponse = params.getString("response");
//
//        byte[] decodedBytes = Base64.getDecoder().decode(encodedResponse);
//        String decodedString = new String(decodedBytes);
//
//        ObjectMapper objectMapper = new ObjectMapper();
//        JsonNode decodedJson = objectMapper.readTree(decodedString);
//        String code = decodedJson.get("code").asText();
//
//        //check the status of the payment, if it is error, return
//        if ("PAYMENT_ERROR".equals(code)) return;

        Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("Asia/Kolkata"));
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);

        CollectionReference userProfiles = userProfileService.getCollectionReference();
        CollectionReference userMemberships = userMembershipsService.getCollectionReference();
        CollectionReference membershipRef = membershipService.getCollectionReference();

        Query memberQuery = userMemberships.whereEqualTo("phone", phoneNumber);
        Query userQuery = userProfiles.whereEqualTo("phone", phoneNumber);
        Query membershipQuery = membershipRef.whereEqualTo("id", membershipId);

        ApiFuture<QuerySnapshot> querySnapshot1 = memberQuery.get();
        ApiFuture<QuerySnapshot> querySnapshot2 = userQuery.get();
        ApiFuture<QuerySnapshot> querySnapshot3 = membershipQuery.get();

        Membership membership = null;
        for (DocumentSnapshot document : querySnapshot3.get().getDocuments()) {
            membership = document.toObject(Membership.class);
            break;
        }

        UserMemberships userMember = null;
        for (DocumentSnapshot document : querySnapshot1.get().getDocuments()) {
            userMember = document.toObject(UserMemberships.class);

            //change userMember's membership type and Add more coins in wallet
            assert membership != null;
            assert userMember != null;
            userMember.setMembershipType(membership.getMembershipType());
            userMember.setCoins(userMember.getCoins() + membership.getCoinsPerMonth());
            // set start and end date
            userMember.setMembershipStartDate("" + calendar.getTimeInMillis());
            userMember.setLastCoinsCreditedDate("" + calendar.getTimeInMillis());
            calendar.add(Calendar.MONTH, membership.getDuration());

            userMember.setMembershipEndDate("" + calendar.getTimeInMillis());
            break;
        }

        UserProfile user = null;
        PaymentLog plog = new PaymentLog();
        CoinTransactions newTransaction = new CoinTransactions();
        for (DocumentSnapshot document : querySnapshot2.get().getDocuments()) {
            user = document.toObject(UserProfile.class);
            user.setLastPaymentDate("" + new Date().getTime());
            user.setLastPaymentAmount(Integer.valueOf(amount));

//            plog.setPaymentDate(user.getLastPaymentDate());
//            plog.setPhone(user.getPhone());
//            plog.setId(UUID.randomUUID().toString());
//            plog.setAmount(user.getLastPaymentAmount());
//            plog.setType("membership");

            // add this transaction to user's history
            newTransaction.setAmount(membership.getSubscriptionFees());
            newTransaction.setSource("membership");
            newTransaction.setTitle("Upgrade to " + membership.getMembershipType() + " Membership");
            newTransaction.setType(TransactionTypeEnum.CREDIT);
            newTransaction.setSourceId(membershipId);
            newTransaction.setTransactionDate(new Date().getTime());
            newTransaction.setId(UUID.randomUUID().toString());

            break;
        }

        List<String> voucherIds = membership.getVouchers();
        for (String voucherId : voucherIds) {
            UserVouchers userVoucher = new UserVouchers();
            userVoucher.setId(UUID.randomUUID().toString());
            userVoucher.setVoucherId(voucherId);
            userVoucher.setCode(generateCouponCode());
            userVoucher.setPhone(user.getPhone());
            userVoucher.setExpiryDate(Long.parseLong(userMember.getMembershipEndDate()));
            userVoucher.setStatus(VoucherStatusEnum.ACTIVE);
            userVouchersService.save(userVoucher);
        }

        userMembershipsService.save(userMember);
        userProfileService.save(user);
//        paymentLogService.save(plog);
        coinTransactionsService.save(newTransaction);

        // send fcm notification to the frontend for redux update
        String MEMBERSHIP_TOPIC = "subscriptionUpdate";
        sendSubscriptionUpdateWithFcm(user.getFcmToken(), MEMBERSHIP_TOPIC, userMember);

    }

    @ApiOperation(value = "to top-up a user's wallet")
    @PostMapping("/topUp")
    public void topUpWallet(@RequestBody JSONObject params, @RequestParam String phoneNumber, @RequestParam String amount, @RequestParam String coinsToGive) throws ExecutionException, InterruptedException, IOException, FirebaseMessagingException {
//        String encodedResponse = params.getString("response");
//
//        byte[] decodedBytes = Base64.getDecoder().decode(encodedResponse);
//        String decodedString = new String(decodedBytes);
//
//        ObjectMapper objectMapper = new ObjectMapper();
//        JsonNode decodedJson = objectMapper.readTree(decodedString);
//        String code = decodedJson.get("code").asText();
//
//        //check the status of the payment, if it is error, return
//        if ("PAYMENT_ERROR".equals(code)) return;

        JSONObject getUserByPhoneParams = new JSONObject();
        getUserByPhoneParams.put("phoneNumber", phoneNumber);
        JSONObject result = userProfileController.getUserByPhone(getUserByPhoneParams);
        UserProfile user = result.getObject("user", UserProfile.class);
        user.setLastPaymentAmount(Integer.parseInt(amount) / 100);
        user.setLastPaymentDate("" + new Date().getTime());

//        PaymentLog plog = new PaymentLog();
//        plog.setId(UUID.randomUUID().toString());
//        plog.setAmount(Integer.parseInt(amount) / 100);
//        plog.setPhone(user.getPhone());
//        plog.setType("topUp");
//        plog.setPaymentDate("" + new Date().getTime());

        JSONObject getMembershipByPhoneParams = new JSONObject();
        getMembershipByPhoneParams.put("phone", phoneNumber);
        UserMemberships userMember = getMembershipByPhone(getMembershipByPhoneParams);

        if (userMember.getMembershipType() == MembershipEnum.Free) return;

        userMember.setCoins(userMember.getCoins() + Integer.parseInt(coinsToGive));

        // add this transaction to user's history
        CoinTransactions newTransaction = new CoinTransactions();
        newTransaction.setAmount(Integer.parseInt(coinsToGive));
        newTransaction.setSource("wallet");
        newTransaction.setType(TransactionTypeEnum.CREDIT);
        newTransaction.setSourceId(params.getString("id"));
        newTransaction.setTransactionDate(new Date().getTime());
        newTransaction.setTitle("Top up Wallet");
        newTransaction.setSourceId(params.getString("id"));
        newTransaction.setId(UUID.randomUUID().toString());
        newTransaction.setPhone(user.getPhone());


        userMembershipsService.save(userMember);
        userProfileService.save(user);
//        paymentLogService.save(plog);
        coinTransactionsService.save(newTransaction);

        // send fcm notification to the frontend for redux update
        String MEMBERSHIP_TOPIC = "subscriptionUpdate";
        sendSubscriptionUpdateWithFcm(user.getFcmToken(), MEMBERSHIP_TOPIC, userMember);

    }

    @ApiOperation(value = "to revoke a user's membership")
    @GetMapping("/expire")
    public UserMemberships expire(@RequestParam String phoneNumber) throws ExecutionException, InterruptedException, IOException, FirebaseMessagingException {
        JSONObject getMembershipByPhoneParams = new JSONObject();
        getMembershipByPhoneParams.put("phone", phoneNumber);
        UserMemberships userMembership = getMembershipByPhone(getMembershipByPhoneParams);
        userMembership.setMembershipType(MembershipEnum.Free);
        userMembership.setLastCoinsCreditedDate(null);
        userMembershipsService.save(userMembership);
        return userMembership;
    }

    @ApiOperation(value = "Get user's recent 10 transaction history")
    @PostMapping("/getRecentTransactions")
    public List<CoinTransactions> getRecentTransactions(@RequestBody JSONObject params) throws ExecutionException, InterruptedException {
        CollectionReference coinTransactionsRef = coinTransactionsService.getCollectionReference();
        Query transactionQuery = coinTransactionsRef.whereEqualTo("phone", params.getString("phone")).whereGreaterThan("transactionDate", 1).orderBy("transactionDate", Query.Direction.DESCENDING).limit(10);
        ApiFuture<QuerySnapshot> snapshotApiFuture = transactionQuery.get();
        List<CoinTransactions> recentTransactions = new ArrayList<>();
        for (DocumentSnapshot document : snapshotApiFuture.get().getDocuments()) {
            recentTransactions.add(document.toObject(CoinTransactions.class));
        }
        return recentTransactions;
    }

    @ApiOperation(value = "Get user's last month transaction history")
    @PostMapping("/getTransactions")
    public List<CoinTransactions> getTransactions(@RequestBody JSONObject params) throws ExecutionException, InterruptedException {
        long startDate = new Date().getTime();
        long endDate = startDate - 30L * 24 * 60 * 60 * 1000;
        CollectionReference coinTransactionsRef = coinTransactionsService.getCollectionReference();
        Query transactionQuery = coinTransactionsRef.whereEqualTo("phone", params.getString("phone")).whereGreaterThanOrEqualTo("transactionDate", endDate).whereLessThanOrEqualTo("transactionDate", startDate).orderBy("transactionDate", Query.Direction.DESCENDING);
        ApiFuture<QuerySnapshot> snapshotApiFuture = transactionQuery.get();
        List<CoinTransactions> transactions = new ArrayList<>();
        for (DocumentSnapshot document : snapshotApiFuture.get().getDocuments()) {
            transactions.add(document.toObject(CoinTransactions.class));
        }
        return transactions;
    }


    @ApiOperation(value = "TO give user coins after successfully scratching the card")
    @PostMapping("/scratchCardReward")
    public void scratchCardReward(@RequestBody JSONObject params) throws ExecutionException, InterruptedException {
        String phone = params.getString("phone");
        String transactionId = params.getString("coinTransactionId");

        CollectionReference coinTransactionRef = coinTransactionsService.getCollectionReference();
        Query query = coinTransactionRef.whereEqualTo("id", transactionId);
        ApiFuture<QuerySnapshot> querySnapshotApiFuture = query.get();
        CoinTransactions transaction = null;
        for (DocumentSnapshot document : querySnapshotApiFuture.get().getDocuments()) {
            transaction = document.toObject(CoinTransactions.class);
            break;
        }
        transaction.setScratched(true);
        transaction.setTransactionDate(new Date().getTime());

        UserMemberships userMembership = getMembershipByPhone(new JSONObject() {{
            put("phone", phone);
        }});
        userMembership.setCoins(userMembership.getCoins() + transaction.getAmount());

        userMembershipsService.save(userMembership);
        coinTransactionsService.save(transaction);
    }

    @ApiOperation(value = "Get user's coin backs from the sessions")
    @PostMapping("/getRewards")
    public List<CoinTransactions> getRewards(@RequestBody JSONObject params) throws ExecutionException, InterruptedException {
        CollectionReference coinTransactionsRef = coinTransactionsService.getCollectionReference();
        List<String> sources = new ArrayList<>();
        sources.add("coinback");
        sources.add("prize");
        Query transactionQuery = coinTransactionsRef.whereEqualTo("phone", params.getString("phone")).whereIn("source", sources).orderBy("transactionDate", Query.Direction.DESCENDING);
        ApiFuture<QuerySnapshot> snapshotApiFuture = transactionQuery.get();
        List<CoinTransactions> transactions = new ArrayList<>();
        for (DocumentSnapshot document : snapshotApiFuture.get().getDocuments()) {
            transactions.add(document.toObject(CoinTransactions.class));
        }
        return transactions;
    }

    @ApiOperation(value = "To get user's vouchers")
    @PostMapping("/getVouchers")
    public List<Map<String, Object>> getVouchers(@RequestBody JSONObject params) throws ExecutionException, InterruptedException {
        CollectionReference userVouchersRef = userVouchersService.getCollectionReference();
        Query vouchersQuery = userVouchersRef.whereEqualTo("phone", params.getString("phone"));
        ApiFuture<QuerySnapshot> snapshotApiFuture = vouchersQuery.get();

        Map<String, Vouchers> idToVoucherMap = new HashMap<>();
        List<Map<String, Object>> resultantUserVouchers = new ArrayList<>();
        ObjectMapper objectMapper = new ObjectMapper();

        for (DocumentSnapshot document : snapshotApiFuture.get().getDocuments()) {
            UserVouchers userVoucher = document.toObject(UserVouchers.class);

            String voucherId = userVoucher.getVoucherId();
            Vouchers voucherDetails = idToVoucherMap.computeIfAbsent(voucherId, id -> {
                try {
                    return getVoucherDetailFromVoucherId(id);
                } catch (ExecutionException | InterruptedException e) {
                    throw new RuntimeException(e);
                }
            });

            Map<String, Object> userVoucherMap = objectMapper.convertValue(userVoucher, Map.class);
            userVoucherMap.put("title", voucherDetails.getTitle());
            userVoucherMap.put("category", voucherDetails.getCategory());
            userVoucherMap.put("image", voucherDetails.getImage());
            userVoucherMap.put("description", voucherDetails.getDescription());
            userVoucherMap.put("value", voucherDetails.getValue());
            userVoucherMap.put("limit", voucherDetails.getLimit());
            userVoucherMap.put("percent", voucherDetails.getPercent());
            userVoucherMap.put("parentStatus", voucherDetails.getStatus());
            userVoucherMap.put("parentExpiryDate", voucherDetails.getExpiryDate());

            resultantUserVouchers.add(userVoucherMap);
        }

        return resultantUserVouchers;
    }


    // create vouchers api
    @ApiOperation(value = "Create voucher for user")
    @PostMapping("/createVoucher")
    public boolean createVoucher(@RequestBody JSONObject params) throws ExecutionException, InterruptedException {
        /*
         * @params
         * - title
         * - image
         * - value
         * - percent
         * - limit
         * - category
         * - status
         * - expiryDate*/

        Vouchers voucher = new Vouchers();
        voucher.setId(UUID.randomUUID().toString());
        voucher.setTitle(params.getString("title"));
        voucher.setImage(params.getString("image"));
        voucher.setValue(params.getDouble("value"));
        voucher.setPercent(params.getInteger("percent"));
        voucher.setLimit(params.getDouble("limit"));
        voucher.setCategory(params.getString("category"));
        voucher.setStatus(VoucherStatusEnum.valueOf(params.getString("status")));
        voucher.setExpiryDate(params.getLong(params.getString("expiryDate")));

        vouchersService.save(voucher);
        return true;
    }

    // update vouchers api
    @ApiOperation(value = "Update voucher for user")
    @PostMapping("/updateVoucher")
    public void updateVoucher(
            @RequestBody JSONObject params,
            @RequestParam(required = false) String toExpire) throws ExecutionException, InterruptedException {

        CollectionReference userVoucherRef = userVouchersService.getCollectionReference();

        if ("true".equalsIgnoreCase(toExpire)) {
            JSONArray voucherIds = params.getJSONArray("voucherIds");
            if (voucherIds != null) {
                for (int i = 0; i < voucherIds.size(); i++) {
                    String voucherId = voucherIds.getString(i);
                    DocumentReference docRef = userVoucherRef.document(voucherId);
                    HashMap<String, Object> updates = new HashMap<>();
                    updates.put("status", VoucherStatusEnum.EXPIRED);
                    docRef.update(updates);
                }
            }
        } else {
            String voucherId = params.getString("voucherId");
            if (voucherId != null && !voucherId.isEmpty()) {
                DocumentReference docRef = userVoucherRef.document(voucherId);
                HashMap<String, Object> updates = new HashMap<>();
                for (String key : params.keySet()) {
                    if (!"voucherId".equals(key)) {
                        updates.put(key, params.get(key));
                    }
                }
                docRef.update(updates);
            }
        }
    }


    @ApiOperation(value = "Get user's profile and membership document for update")
    @PostMapping("/getUserUpdates")
    public JSONObject getUserUpdates(@RequestBody JSONObject params) throws ExecutionException, InterruptedException {
        CollectionReference userProfiles = userProfileService.getCollectionReference();
        CollectionReference userMemberships = userMembershipsService.getCollectionReference();

        Query userQuery = userProfiles.whereEqualTo("phone", params.getString("phoneNumber"));
        Query userMembershipQuery = userMemberships.whereEqualTo("phone", params.getString("phoneNumber"));
        ApiFuture<QuerySnapshot> querySnapshot1 = userQuery.get();
        ApiFuture<QuerySnapshot> querySnapshot2 = userMembershipQuery.get();

        UserProfile userProfile = null;
        for (DocumentSnapshot document : querySnapshot1.get().getDocuments()) {
            userProfile = document.toObject(UserProfile.class);
            break;
        }

        UserMemberships userMembership = null;
        for (DocumentSnapshot document : querySnapshot2.get().getDocuments()) {
            userMembership = document.toObject(UserMemberships.class);
            break;
        }
        JSONObject userUpdates = new JSONObject();
        userUpdates.put("userProfile", userProfile);
        userUpdates.put("userMembership", userMembership);
        return userUpdates;
    }

    @ApiOperation(value = "Get membership data by phone")
    @PostMapping("/getMembershipByPhone")
    public UserMemberships getMembershipByPhone(@RequestBody JSONObject requestBody) throws ExecutionException, InterruptedException {

        CollectionReference userMemberships = userMembershipsService.getCollectionReference();

        Query userMembership = userMemberships.whereEqualTo("phone", requestBody.getString("phone"));
        ApiFuture<QuerySnapshot> querySnapshot1 = userMembership.get();

        UserMemberships userMemberships1 = null;
        for (DocumentSnapshot document : querySnapshot1.get().getDocuments()) {
            userMemberships1 = document.toObject(UserMemberships.class);
            break;
        }

        return userMemberships1;
    }

    public UserMemberships createNewMembership(String phone, String userId) {
        UserMemberships userMembership = new UserMemberships();

        userMembership.setId(UUID.randomUUID().toString());
        userMembership.setUserId(userId);
        userMembership.setPhone(phone);

        userMembershipsService.save(userMembership);

        return userMembership;
    }

    private void sendSubscriptionUpdateWithFcm(String fcmToken, String topic, UserMemberships userMembership) throws FirebaseMessagingException {
        List<String> fcmTokens = new ArrayList<>();
        fcmTokens.add(fcmToken);

        try {
            FirebaseMessaging.getInstance().subscribeToTopic(fcmTokens, topic);
        } catch (FirebaseMessagingException e) {
            throw new RuntimeException(e);
        }

        try {
            Message message = Message.builder().setTopic(topic).putData("runUpdate", "true").putData("type", "subscriptionUpdate").putData("userMembership", JSONObject.toJSONString(userMembership)).build();

            String response = FirebaseMessaging.getInstance().send(message);
        } catch (FirebaseMessagingException e) {
            System.err.println("Failed to send weekly contribution reminder: " + e.getMessage());
        }
        FirebaseMessaging.getInstance().unsubscribeFromTopic(fcmTokens, topic);
    }

    public Vouchers getVoucherDetailFromVoucherId(String voucherId) throws ExecutionException, InterruptedException {
        CollectionReference vouchersRef = vouchersService.getCollectionReference();
        Query query = vouchersRef.whereEqualTo("id", voucherId);
        ApiFuture<QuerySnapshot> querySnapshotApiFuture = query.get();
        Vouchers voucher = null;
        for (DocumentSnapshot document : querySnapshotApiFuture.get().getDocuments()) {
            voucher = document.toObject(Vouchers.class);
            break;
        }
        return voucher;
    }
}