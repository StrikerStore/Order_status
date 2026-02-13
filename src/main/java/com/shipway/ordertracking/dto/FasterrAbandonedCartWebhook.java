package com.shipway.ordertracking.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class FasterrAbandonedCartWebhook {
    
    @JsonProperty("zip")
    private String zip;
    
    @JsonProperty("city")
    private String city;
    
    @JsonProperty("state")
    private String state;
    
    @JsonProperty("name")
    private String name;
    
    @JsonProperty("phone")
    private String phone;
    
    @JsonProperty("country")
    private String country;
    
    @JsonProperty("address1")
    private String address1;
    
    @JsonProperty("last_name")
    private String lastName;
    
    @JsonProperty("first_name")
    private String firstName;
    
    @JsonProperty("line_1")
    private String line1;
    
    @JsonProperty("email")
    private String email;
    
    @JsonProperty("payment_method")
    private String paymentMethod;
    
    @JsonProperty("custom_attributes")
    private CustomAttributes customAttributes;
    
    @JsonProperty("webhookUrl")
    private String webhookUrl;
    
    @JsonProperty("executionMode")
    private String executionMode;

    public FasterrAbandonedCartWebhook() {
    }

    public String getZip() {
        return zip;
    }

    public void setZip(String zip) {
        this.zip = zip;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getCountry() {
        return country;
    }

    public void setCountry(String country) {
        this.country = country;
    }

    public String getAddress1() {
        return address1;
    }

    public void setAddress1(String address1) {
        this.address1 = address1;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLine1() {
        return line1;
    }

    public void setLine1(String line1) {
        this.line1 = line1;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPaymentMethod() {
        return paymentMethod;
    }

    public void setPaymentMethod(String paymentMethod) {
        this.paymentMethod = paymentMethod;
    }

    public CustomAttributes getCustomAttributes() {
        return customAttributes;
    }

    public void setCustomAttributes(CustomAttributes customAttributes) {
        this.customAttributes = customAttributes;
    }

    public String getWebhookUrl() {
        return webhookUrl;
    }

    public void setWebhookUrl(String webhookUrl) {
        this.webhookUrl = webhookUrl;
    }

    public String getExecutionMode() {
        return executionMode;
    }

    public void setExecutionMode(String executionMode) {
        this.executionMode = executionMode;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CustomAttributes {
        @JsonProperty("shopifyCartToken")
        private String shopifyCartToken;
        
        @JsonProperty("landing_page_url")
        private String landingPageUrl;
        
        @JsonProperty("ipv4_address")
        private String ipv4Address;

        public CustomAttributes() {
        }

        public String getShopifyCartToken() {
            return shopifyCartToken;
        }

        public void setShopifyCartToken(String shopifyCartToken) {
            this.shopifyCartToken = shopifyCartToken;
        }

        public String getLandingPageUrl() {
            return landingPageUrl;
        }

        public void setLandingPageUrl(String landingPageUrl) {
            this.landingPageUrl = landingPageUrl;
        }

        public String getIpv4Address() {
            return ipv4Address;
        }

        public void setIpv4Address(String ipv4Address) {
            this.ipv4Address = ipv4Address;
        }
    }
}
