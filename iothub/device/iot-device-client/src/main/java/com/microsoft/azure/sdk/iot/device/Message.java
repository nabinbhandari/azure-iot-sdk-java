// Copyright (c) Microsoft. All rights reserved.
// Licensed under the MIT license. See LICENSE file in the project root for full license information.

package com.microsoft.azure.sdk.iot.device;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;

@Slf4j
public class Message
{
    // ----- Constants -----

    public static final Charset DEFAULT_IOTHUB_MESSAGE_CHARSET = StandardCharsets.UTF_8;

    private static final String DATE_TIME_FORMAT = "yyyy-MM-dd_HH:mm:ss.SSSSSSS";

    private static final String SECURITY_CLIENT_JSON_ENCODING = "application/json";

    private static final String UTC_TIMEZONE = "UTC";

    // ----- Data Fields -----

    /**
     * [Required for two way requests] Used to correlate two-way communication.
     * Format: A case-sensitive string (up to 128 char long) of ASCII 7-bit alphanumeric chars
     * plus {'-', ':', '/', '\', '.', '+', '%', '_', '#', '*', '?', '!', '(', ')', ',', '=', '@', ';', '$', '''}.
     * Non-alphanumeric characters are from URN RFC.
     */
    private String messageId;

    /**
     * Destination of the message
     */
    @SuppressWarnings("unused") // Used in getter, leaving for future expansion
    private String to;

    /**
     * Expiry time in milliseconds. Optional
     */
    private long expiryTime;

    /**
     * Used in message responses and feedback
     */
    private String correlationId;

    /**
     * [Required in feedback messages] Used to specify the entity creating the message.
     */
    private String userId;

    /**
     * [Stamped on servicebound messages by IoT Hub] The authenticated id used to send this message.
     */
    private String connectionDeviceId;

    /**
     * [Optional] Used to specify the type of message exchanged between Iot Hub and Device
     */
    private MessageType messageType;

    /**
     * [Optional] Used to specify the sender device client for multiplexing scenarios
     */
    private IotHubConnectionString iotHubConnectionString;

    private CorrelatingMessageCallback correlatingMessageCallback;

    /**
     * [Optional] Used to specify the sender device client for multiplexing scenarios
     */
    private Object correlatingMessageCallbackContext;

    private String connectionModuleId;
    private String inputName;
    private String outputName;

    @SuppressWarnings("unused") // This is not set anywhere but is used in a method
    private String deliveryAcknowledgement;

    /**
     * User-defined properties.
     */
    private ArrayList<MessageProperty> properties;

    /**
     * The message body
     */
    private byte[] body;

    /**
     * Message routing options
     */
    private String contentType;
    private String contentEncoding;

    private Date creationTimeUTC;

    /**
     * Security Client flag
     */
    private boolean isSecurityClient;

    /**
     * The DTDL component name from where the telemetry message has originated. This field is only relevant
     * for Plug and Play certified devices.
     *
     * @see <a href="https://docs.microsoft.com/en-us/azure/iot-develop/overview-iot-plug-and-play">What is IoT Plug and Play?</a>
     */
    @Getter
    @Setter
    String componentName;

    // ----- Constructors -----

    /**
     * Constructor.
     */
    public Message()
    {
        initialize();
    }

    /**
     * Constructor.
     * @param stream A stream to provide the body of the new Message instance.
     */
    private Message(ByteArrayInputStream stream)
    {
        initialize();
    }

    /**
     * Constructor.
     * @param body The body of the new Message instance.
     */
    public Message(byte[] body)
    {
        if (body == null)
        {
            throw new IllegalArgumentException("Message body cannot be 'null'.");
        }

        initialize();

        this.body = body;
    }

    /**
     * Constructor.
     * @param body The body of the new Message instance. It is internally serialized to a byte array using UTF-8 encoding.
     */
    public Message(String body)
    {
        if (body == null)
        {
            throw new IllegalArgumentException("Message body cannot be 'null'.");
        }

        initialize();

        this.body = body.getBytes(DEFAULT_IOTHUB_MESSAGE_CHARSET);
        this.setContentEncoding(DEFAULT_IOTHUB_MESSAGE_CHARSET.name());
    }

    
    // ----- Public Methods -----

    /**
     * The stream content of the body.
     * @return always returns null.
     */
    @SuppressWarnings("SameReturnValue")
    public ByteArrayOutputStream getBodyStream()
    {
        return null;
    }

    /**
     * The byte content of the body.
     * @return A copy of this Message body, as a byte array.
     */
    public byte[] getBytes()
    {
        byte[] bodyClone = null;

        if (this.body != null) {
            bodyClone = Arrays.copyOf(this.body, this.body.length);
        }

        return bodyClone;
    }

    /**
     * Gets the values of user-defined properties of this Message.
     * @param name Name of the user-defined property to search for.
     * @return The value of the property if it is set, or null otherwise.
     */
    public String getProperty(String name)
    {
        MessageProperty messageProperty = null;

        for (MessageProperty currentMessageProperty: this.properties)
        {
            if (currentMessageProperty.hasSameName(name))
            {
                messageProperty = currentMessageProperty;
                break;
            }
        }

        if (messageProperty == null)
        {
            return null;
        }

        return messageProperty.getValue();
    }

    /**
     * Adds or sets user-defined properties of this Message.
     * @param name Name of the property to be set.
     * @param value Value of the property to be set.
     * @exception IllegalArgumentException If any of the arguments provided is null.
     */
    public void setProperty(String name, String value)
    {
        if (name == null)
        {
            throw new IllegalArgumentException("Property name cannot be 'null'.");
        }

        if (value == null)
        {
            throw new IllegalArgumentException("Property value cannot be 'null'.");
        }

        MessageProperty messageProperty = null;

        for (MessageProperty currentMessageProperty: this.properties)
        {
            if (currentMessageProperty.hasSameName(name))
            {
                messageProperty = currentMessageProperty;
                break;
            }
        }

        if (messageProperty != null)
        {
            this.properties.remove(messageProperty);
        }

        this.properties.add(new MessageProperty(name, value));
    }

    /**
     * Returns a copy of the message properties.
     *
     * @return a copy of the message properties.
     */
    public MessageProperty[] getProperties()
    {
        return properties.toArray(new MessageProperty[this.properties.size()]);
    }

    // ----- Private Methods -----

    /**
     * Internal initializer method for a new Message instance.
     */
    private void initialize()
    {
        this.messageId = UUID.randomUUID().toString();
        this.correlationId = UUID.randomUUID().toString();
        this.properties = new ArrayList<>();
        this.isSecurityClient = false;
    }

    /**
     * Verifies whether the message is expired or not
     * @return true if the message is expired, false otherwise
     */
    public boolean isExpired()
    {
        boolean messageExpired;

        if (this.expiryTime == 0)
        {
            messageExpired = false;
        }
        else
        {
            long currentTime = System.currentTimeMillis();
            if (currentTime > expiryTime)
            {
                log.warn("The message with correlation id {} expired", this.getCorrelationId());
                messageExpired = true;
            }
            else
            {
                messageExpired = false;
            }
        }

        return messageExpired;
    }

    /**
     * Getter for the messageId property
     * @return The property value
     */
    public String getMessageId()
    {
        return messageId;
    }

    /**
     * Setter for the messageId property
     * @param messageId The string containing the property value
     */
    public void setMessageId(String messageId)
    {
        this.messageId = messageId;
    }

    public void setUserId(String userId)
    {
        this.userId = userId;
    }

    /**
     * Getter for the correlationId property
     * @return The property value
     */
    public String getCorrelationId()
    {
        if (correlationId == null)
        {
            return "";
        }

        return correlationId;
    }

    /**
     * Setter for the correlationId property
     * @param correlationId The string containing the property value
     */
    public void setCorrelationId(String correlationId)
    {
        this.correlationId = correlationId;
    }

    /**
     * Setter for the expiryTime property. This setter uses relative time, not absolute time.
     * @param timeOut The time out for the message, in milliseconds, from the current time.
     */
    public void setExpiryTime(long timeOut)
    {
        long currentTime = System.currentTimeMillis();
        this.expiryTime = currentTime + timeOut;
        log.trace("The message with messageid {} has expiry time in {} milliseconds and the message will expire on {}", this.getMessageId(), timeOut, new Date(this.expiryTime));
    }

    /**
     * Setter for the expiryTime property using absolute time
     * @param absoluteTimeout The time out for the message, in milliseconds.
     */
    public void setAbsoluteExpiryTime(long absoluteTimeout)
    {
        if (absoluteTimeout < 0)
        {
            throw new IllegalArgumentException("ExpiryTime may not be negative");
        }

        this.expiryTime = absoluteTimeout;
    }

    /**
     * Getter for the Message type
     * @return the Message type value
     */
    public MessageType getMessageType()
    {
        return this.messageType;
    }

    public void setConnectionDeviceId(String connectionDeviceId)
    {
        this.connectionDeviceId = connectionDeviceId;
    }

    public void setConnectionModuleId(String connectionModuleId)
    {
        this.connectionModuleId = connectionModuleId;
    }

    /**
     * Set the output channel name to send to. Used in routing for module communications
     * @param outputName the output channel name to send to
     */
    public void setOutputName(String outputName)
    {
        this.outputName = outputName;
    }

    /**
     * Set the input name of the message, used in routing for module communications
     * @param inputName the input channel the message was received from
     */
    public void setInputName(String inputName)
    {
        this.inputName = inputName;
    }

    /**
     * Setter for the Message type
     * @param type The enum containing the Message type value
     */
    public void setMessageType(MessageType type)
    {
        this.messageType = type;
    }

    /**
     * Getter for the To system property
     * @return the To value
     */
    public String getTo()
    {
        return this.to;
    }

    public String getConnectionDeviceId()
    {
        return connectionDeviceId;
    }

    public String getConnectionModuleId()
    {
        return connectionModuleId;
    }

    public String getInputName()
    {
        return inputName;
    }

    public String getOutputName()
    {
        return outputName;
    }

    /**
     * Getter for the delivery acknowledgement system property
     * @return the delivery acknowledgement value
     */
    public String getDeliveryAcknowledgement()
    {
        return this.deliveryAcknowledgement;
    }

    /**
     * Getter for the User ID system property
     * @return the User ID value
     */
    public String getUserId ()
    {
        return this.userId;
    }

    /**
     * Getter for the iotHubConnectionString property
     * @return the iotHubConnectionString value
     */
    public IotHubConnectionString getIotHubConnectionString()
    {
        return iotHubConnectionString;
    }

    /**
     * Setter for the iotHubConnectionString type
     * @param iotHubConnectionString The iotHubConnectionString value to set
     */
    public void setIotHubConnectionString(IotHubConnectionString iotHubConnectionString)
    {
        this.iotHubConnectionString = iotHubConnectionString;
    }

    /**
     * Return the message's content type. This value is null by default
     * @return the message's content type
     */
    public String getContentType()
    {
        return this.contentType;
    }

    /**
     * Set the content type of this message. Used in message routing.
     *
     * @param contentType the content type of the message. May be null if you don't want to specify a content type.
     */
    public final void setContentType(String contentType)
    {
        this.contentType = contentType;
    }

    /**
     * Returns this message's content encoding. This value is null by default
     * @return the message's content encoding.
     */
    public String getContentEncoding()
    {
        return this.contentEncoding;
    }

    /**
     * Set the content encoding of this message. Used in message routing.
     * @param contentEncoding the content encoding of the message. May be null if you don't want to specify a content encoding.
     */
    public void setContentEncoding(String contentEncoding)
    {
        this.contentEncoding = contentEncoding;
    }

    public Date getCreationTimeUTC()
    {
        return this.creationTimeUTC;
    }

    /**
     * Returns the iot hub accepted format for the creation time utc
     *
     * ex:
     * oct 1st, 2018 yields
     * 2008-10-01T17:04:32.0000000
     *
     * @return the iot hub accepted format for the creation time utc
     */
    public String getCreationTimeUTCString()
    {
        if (this.creationTimeUTC == null)
        {
            return null;
        }

        SimpleDateFormat sdf = new SimpleDateFormat(DATE_TIME_FORMAT);
        sdf.setTimeZone(TimeZone.getTimeZone(UTC_TIMEZONE));
        return sdf.format(this.creationTimeUTC).replace("_", "T") + "Z";
    }

    public final void setCreationTimeUTC(Date creationTimeUTC)
    {
        this.creationTimeUTC = creationTimeUTC;
    }

    public void setAsSecurityMessage()
    {
        // Set the message as json encoding
        this.contentEncoding = SECURITY_CLIENT_JSON_ENCODING;
        this.isSecurityClient = true;
    }

    public boolean isSecurityMessage()
    {
        return this.isSecurityClient;
    }

    @Override
    public String toString()
    {
        StringBuilder s = new StringBuilder();
        s.append(" Message details: ");
        if (this.correlationId != null && !this.correlationId.isEmpty())
        {
            s.append("Correlation Id [").append(this.correlationId).append("] ");
        }

        if (this.messageId != null && !this.messageId.isEmpty())
        {
            s.append("Message Id [").append(this.messageId).append("] ");
        }

        return s.toString();
    }

    public void setCorrelatingMessageCallback(CorrelatingMessageCallback correlatingMessageCallback) {
        this.correlatingMessageCallback = correlatingMessageCallback;
    }

    public CorrelatingMessageCallback getCorrelatingMessageCallback() {
        return correlatingMessageCallback;
    }

    public void setCorrelatingMessageCallbackContext(Object correlatingMessageCallbackContext) {
        this.correlatingMessageCallbackContext = correlatingMessageCallbackContext;
    }

    public Object getCorrelatingMessageCallbackContext() {
        return correlatingMessageCallbackContext;
    }
}