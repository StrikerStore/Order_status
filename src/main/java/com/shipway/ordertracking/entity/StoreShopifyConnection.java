package com.shipway.ordertracking.entity;

import jakarta.persistence.*;

/**
 * Maps Shopify store key ({@code brand_name} = {@code shopify.accounts} map key, e.g. STRIKER STORE)
 * to carrier / DB {@code account_code} used in {@code order_tracking} and {@code labels}.
 * <p>
 * Create table when using validate mode, for example:
 * {@code CREATE TABLE store_shopify_connections (id INT AUTO_INCREMENT PRIMARY KEY, brand_name VARCHAR(100) NOT NULL, account_code VARCHAR(100) NOT NULL);}
 */
@Entity
@Table(name = "store_shopify_connections")
public class StoreShopifyConnection {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "brand_name", nullable = false, length = 100)
    private String brandName;

    @Column(name = "account_code", nullable = false, length = 100)
    private String accountCode;

    public StoreShopifyConnection() {
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getBrandName() {
        return brandName;
    }

    public void setBrandName(String brandName) {
        this.brandName = brandName;
    }

    public String getAccountCode() {
        return accountCode;
    }

    public void setAccountCode(String accountCode) {
        this.accountCode = accountCode;
    }
}
