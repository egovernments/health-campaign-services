package org.egov.web.notification.push.utils;

public class ErrorConstants {

	public static final String MISSING_DEVICE_TOKEN_CODE = "PUSH_MISSING_DEVICE_TOKEN";
	public static final String MISSING_DEVICE_TOKEN_MSG = "Device token is mandatory in the request.";

	public static final String INVALID_DEVICE_TYPE_CODE = "PUSH_INVALID_DEVICE_TYPE";
	public static final String INVALID_DEVICE_TYPE_MSG = "Device type must be one of: ANDROID, IOS, WEB.";

	public static final String PUSH_MISSING_TITLE_CODE = "PUSH_MISSING_TITLE";
	public static final String PUSH_MISSING_TITLE_MSG = "Title is mandatory for push notification.";

	public static final String PUSH_MISSING_BODY_CODE = "PUSH_MISSING_BODY";
	public static final String PUSH_MISSING_BODY_MSG = "Body is mandatory for push notification.";

	public static final String PUSH_MISSING_RECIPIENTS_CODE = "PUSH_MISSING_RECIPIENTS";
	public static final String PUSH_MISSING_RECIPIENTS_MSG = "At least one of userUuids or deviceTokens must be provided.";

}
