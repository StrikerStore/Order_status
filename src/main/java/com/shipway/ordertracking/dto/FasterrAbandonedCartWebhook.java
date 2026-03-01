package com.shipway.ordertracking.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Fasterr abandoned cart webhook payload. Supports:
 * - { "attributes": { cart data } }
 * - { "body": { cart data } }  (Fasterr/n8n style wrapper)
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class FasterrAbandonedCartWebhook {

    @JsonProperty("attributes")
    private Attributes attributes;

    @JsonProperty("body")
    private Attributes body;

    public FasterrAbandonedCartWebhook() {
    }

    /** Cart data from either "attributes" or "body" wrapper. */
    public Attributes getAttributes() {
        return attributes != null ? attributes : body;
    }

    public void setAttributes(Attributes attributes) {
        this.attributes = attributes;
    }

    public Attributes getBody() {
        return body;
    }

    public void setBody(Attributes body) {
        this.body = body;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Attributes {
        @JsonProperty("cart_id")
        private String cartId;

        @JsonProperty("phone_number")
        private String phoneNumber;

        @JsonProperty("first_name")
        private String firstName;

        @JsonProperty("checkout_url")
        private String checkoutUrl;

        @JsonProperty("custom_attributes")
        private CustomAttributes customAttributes;

        @JsonProperty("shipping_address")
        private Address shippingAddress;

        @JsonProperty("billing_address")
        private Address billingAddress;

        @JsonProperty("items")
        private List<CartItem> items;

        public String getCartId() {
            return cartId;
        }

        public void setCartId(String cartId) {
            this.cartId = cartId;
        }

        public String getPhoneNumber() {
            return phoneNumber;
        }

        public void setPhoneNumber(String phoneNumber) {
            this.phoneNumber = phoneNumber;
        }

        public String getFirstName() {
            return firstName;
        }

        public void setFirstName(String firstName) {
            this.firstName = firstName;
        }

        public String getCheckoutUrl() {
            return checkoutUrl;
        }

        public void setCheckoutUrl(String checkoutUrl) {
            this.checkoutUrl = checkoutUrl;
        }

        public CustomAttributes getCustomAttributes() {
            return customAttributes;
        }

        public void setCustomAttributes(CustomAttributes customAttributes) {
            this.customAttributes = customAttributes;
        }

        public Address getShippingAddress() {
            return shippingAddress;
        }

        public void setShippingAddress(Address shippingAddress) {
            this.shippingAddress = shippingAddress;
        }

        public Address getBillingAddress() {
            return billingAddress;
        }

        public void setBillingAddress(Address billingAddress) {
            this.billingAddress = billingAddress;
        }

        public List<CartItem> getItems() {
            return items;
        }

        public void setItems(List<CartItem> items) {
            this.items = items;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CustomAttributes {
        @JsonProperty("landing_page_url")
        private String landingPageUrl;

        public String getLandingPageUrl() {
            return landingPageUrl;
        }

        public void setLandingPageUrl(String landingPageUrl) {
            this.landingPageUrl = landingPageUrl;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Address {
        @JsonProperty("phone")
        private String phone;

        public String getPhone() {
            return phone;
        }

        public void setPhone(String phone) {
            this.phone = phone;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CartItem {
        @JsonProperty("product_id")
        private Long productId;

        @JsonProperty("img_url")
        private String imgUrl;

        public Long getProductId() {
            return productId;
        }

        public void setProductId(Long productId) {
            this.productId = productId;
        }

        public String getImgUrl() {
            return imgUrl;
        }

        public void setImgUrl(String imgUrl) {
            this.imgUrl = imgUrl;
        }
    }
}
