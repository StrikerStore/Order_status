package com.shipway.ordertracking.repository;

import com.shipway.ordertracking.entity.StoreShopifyConnection;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StoreShopifyConnectionRepository extends JpaRepository<StoreShopifyConnection, Integer> {

    List<StoreShopifyConnection> findAllByOrderByBrandNameAsc();
}
