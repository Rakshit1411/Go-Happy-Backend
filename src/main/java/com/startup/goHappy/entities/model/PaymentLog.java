package com.startup.goHappy.entities.model;

import com.startup.goHappy.enums.PaymentStatusEnum;
import lombok.Data;

@Data
public class PaymentLog {
	@DocumentId
	private String id;
	private String phone;
	private String paymentDate;
	private Integer amount;
	private String type; //contribution, workshop
	private String orderId;
	private PaymentStatusEnum status = PaymentStatusEnum.INITIATED;
}
