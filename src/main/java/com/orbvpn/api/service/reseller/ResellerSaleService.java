package com.orbvpn.api.service.reseller;

import com.orbvpn.api.domain.entity.Group;
import com.orbvpn.api.domain.entity.Reseller;
import com.orbvpn.api.domain.entity.ResellerSale;
import com.orbvpn.api.domain.entity.User;
import com.orbvpn.api.repository.ResellerSaleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ResellerSaleService {

    private final ResellerSaleRepository resellerSaleRepository;

    public void createSale(Reseller reseller, User user, Group group, BigDecimal price) {

        ResellerSale sale = ResellerSale.builder()
                .reseller(reseller)
                .user(user)
                .group(group)
                .price(price)
                .build();

        resellerSaleRepository.save(sale);
    }

    public List<ResellerSale> getTotalSaleOfReseller(int resellerId) {
        return resellerSaleRepository.getTotalSalesOfReseller(resellerId);
    }

    public List<ResellerSale> getLastMonthSalesOfReseller(int resellerId) {
        LocalDateTime lastMonthDate = LocalDateTime.now().minusMonths(1);
        return resellerSaleRepository.getSalesOfResellerByDate(resellerId, lastMonthDate);
    }

    /**
     * Returns the count of sales made by a reseller in the last month.
     */
    public int getMonthlySalesCount(int resellerId) {
        return getLastMonthSalesOfReseller(resellerId).size();
    }

    /**
     * Returns the total revenue from sales in the last month.
     */
    public BigDecimal getMonthlySalesRevenue(int resellerId) {
        return getLastMonthSalesOfReseller(resellerId).stream()
                .map(ResellerSale::getPrice)
                .filter(price -> price != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Returns the total lifetime revenue for a reseller.
     */
    public BigDecimal getTotalSalesRevenue(int resellerId) {
        return getTotalSaleOfReseller(resellerId).stream()
                .map(ResellerSale::getPrice)
                .filter(price -> price != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * @deprecated Use getMonthlySalesCount instead
     */
    @Deprecated
    public int getMonthlySalesOfReseller(int resellerId) {
        return getMonthlySalesCount(resellerId);
    }
}
