package com.orbvpn.api.domain.dto;

import com.orbvpn.api.domain.dto.PackagePrice;

public class PackagePrice {
  private int groupId;
  private int serviceGroupId;
  private float price;

  // Constructor, getters and setters
  public PackagePrice(int groupId, int serviceGroupId, float price) {
    this.groupId = groupId;
    this.serviceGroupId = serviceGroupId;
    this.price = price;
  }

  // Getters and setters
  public int getGroupId() {
    return groupId;
  }

  public int getServiceGroupId() {
    return serviceGroupId;
  }

  public float getPrice() {
    return price;
  }

  public void setPrice(float price) {
    this.price = price;
  }
}
