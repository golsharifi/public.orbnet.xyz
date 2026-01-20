package com.orbvpn.api.service.reseller;

import com.orbvpn.api.domain.dto.*;
import com.orbvpn.api.domain.entity.*;
import static com.orbvpn.api.domain.entity.AdminAuditLog.*;
import com.orbvpn.api.domain.enums.ResellerLevelName;
import com.orbvpn.api.domain.enums.RoleName;
import com.orbvpn.api.exception.InsufficientFundsException;
import com.orbvpn.api.exception.InternalException;
import com.orbvpn.api.exception.NotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import com.orbvpn.api.mapper.*;
import com.orbvpn.api.repository.*;
import com.orbvpn.api.service.PasswordService;
import com.orbvpn.api.service.RoleService;
import com.orbvpn.api.service.ServiceGroupService;
import com.orbvpn.api.service.audit.AdminAuditService;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional
public class ResellerService {

    // Low balance threshold for notifications (configurable)
    private static final BigDecimal LOW_BALANCE_THRESHOLD = new BigDecimal("10.00");

    @Setter
    private ServiceGroupService serviceGroupService;
    private final PasswordService passwordService;
    private final RoleService roleService;
    private final AdminAuditService adminAuditService;

    private final ResellerViewMapper resellerViewMapper;
    private final ResellerEditMapper resellerEditMapper;
    private final ResellerLevelEditMapper resellerLevelEditMapper;
    private final ResellerLevelViewMapper resellerLevelViewMapper;
    private final ResellerCreditViewMapper resellerCreditViewMapper;
    private final ResellerLevelCoefficientsMapper resellerLevelCoefficientsMapper;

    private final ResellerRepository resellerRepository;
    private final ResellerAddCreditRepository resellerAddCreditRepository;
    private final ResellerLevelRepository resellerLevelRepository;
    private final ResellerLevelCoefficientsRepository resellerLevelCoefficientsRepository;

    public Reseller getOwnerReseller() {
        return getResellerById(1);
    }

    public ResellerView createReseller(ResellerCreate resellerCreate) {
        log.info("Creating a reseller with data {} ", resellerCreate);

        Reseller reseller = resellerEditMapper.create(resellerCreate);
        reseller.setLevelSetDate(LocalDateTime.now());

        User user = reseller.getUser();
        user.setRole(roleService.getByName(RoleName.RESELLER));
        passwordService.setPassword(user, resellerCreate.getPassword());

        resellerRepository.save(reseller);
        ResellerView resellerView = resellerViewMapper.toView(reseller);

        log.info("Created a reseller with view {}", resellerView);
        return resellerView;
    }

    public ResellerView getReseller(int id) {
        Reseller reseller = getResellerById(id);
        return resellerViewMapper.toView(reseller);
    }

    public List<ResellerView> getEnabledResellers() {
        return resellerRepository.findAllByEnabled(true)
                .stream()
                .map(resellerViewMapper::toView)
                .collect(Collectors.toList());
    }

    public BigDecimal getTotalResellersCredit() {
        BigDecimal total = resellerRepository.getResellersTotalCredit();
        return total != null ? total : BigDecimal.ZERO;
    }

    public ResellerView editReseller(int id, ResellerEdit resellerEdit) {
        log.info("Editing reseller with id {} with data {}", id, resellerEdit);

        Reseller reseller = getResellerById(id);
        resellerEditMapper.edit(reseller, resellerEdit);

        resellerRepository.save(reseller);
        ResellerView resellerView = resellerViewMapper.toView(reseller);

        log.info("Edited reseller {}", resellerView);
        return resellerView;
    }

    public ResellerView deleteReseller(int id) {
        log.info("Deleting reseller with id {}", id);
        Reseller reseller = getResellerById(id);

        resellerRepository.delete(reseller);

        return resellerViewMapper.toView(reseller);
    }

    public void deleteAllByUser(User user) {
        // userRepository.updateResellerId(user.getReseller().getId(), newResellerId);
        resellerRepository.deleteAllByUser(user);
    }

    public ResellerView addResellerServiceGroup(int resellerId, int serviceGroupId) {
        Reseller reseller = getResellerById(resellerId);
        ServiceGroup serviceGroup = serviceGroupService.getById(serviceGroupId);

        reseller.getServiceGroups().add(serviceGroup);

        resellerRepository.save(reseller);
        return resellerViewMapper.toView(reseller);
    }

    public ResellerView removeResellerServiceGroup(int resellerId, int serviceGroupId) {
        Reseller reseller = getResellerById(resellerId);
        ServiceGroup serviceGroup = serviceGroupService.getById(serviceGroupId);

        reseller.getServiceGroups().remove(serviceGroup);

        resellerRepository.save(reseller);
        return resellerViewMapper.toView(reseller);
    }

    public ResellerView setResellerLevel(int resellerId, ResellerLevelName name) {
        log.info("Updating reseller {} level {}", resellerId, name);
        Reseller reseller = getResellerById(resellerId);

        reseller.setLevel(getResellerLevel(name));
        reseller.setLevelSetDate(LocalDateTime.now());
        resellerRepository.save(reseller);

        log.info("Reseller {} updated level {}", resellerId, name);

        return resellerViewMapper.toView(reseller);
    }

    public ResellerView addResellerCredit(int resellerId, BigDecimal credit) {
        return addResellerCredit(resellerId, credit, "Admin credit", null);
    }

    public ResellerView addResellerCredit(int resellerId, BigDecimal credit, String reason, String performedBy) {
        log.info("Adding reseller {} credit {} reason: {}", resellerId, credit, reason);
        Reseller reseller = getResellerById(resellerId);

        BigDecimal curCredit = reseller.getCredit();
        BigDecimal newBalance = curCredit.add(credit);
        reseller.setCredit(newBalance);

        // Get performer info if not provided
        String performer = performedBy;
        if (performer == null) {
            performer = getCurrentUserEmail();
        }

        ResellerAddCredit resellerAddCredit = ResellerAddCredit.createCredit(
                reseller, credit, newBalance, reason, performer);

        resellerRepository.save(reseller);
        resellerAddCreditRepository.save(resellerAddCredit);

        // Audit log
        Map<String, Object> before = new HashMap<>();
        before.put("balance", curCredit);

        Map<String, Object> after = new HashMap<>();
        after.put("balance", newBalance);
        after.put("amountAdded", credit);
        after.put("reason", reason);

        adminAuditService.logResellerCreditAction(
                ACTION_ADD_RESELLER_CREDIT,
                reseller.getId(), reseller.getUser().getEmail(),
                before, after,
                String.format("Added %s credit to reseller %s. Reason: %s",
                        credit, reseller.getUser().getEmail(), reason));

        log.info("Added reseller {} credit {}. New balance: {}", resellerId, credit, newBalance);

        return resellerViewMapper.toView(reseller);
    }

    public ResellerView deductResellerCredit(int resellerId, BigDecimal credit) {
        return deductResellerCredit(resellerId, credit, "Admin deduction", null);
    }

    public ResellerView deductResellerCredit(int resellerId, BigDecimal credit, String reason, String performedBy) {
        log.info("Deducting reseller {} credit {} reason: {}", resellerId, credit, reason);
        Reseller reseller = getResellerById(resellerId);

        BigDecimal curCredit = reseller.getCredit();
        BigDecimal newBalance = curCredit.subtract(credit);

        // Check for insufficient funds
        if (newBalance.compareTo(BigDecimal.ZERO) < 0) {
            log.warn("Insufficient funds for reseller {}. Current: {}, Requested: {}",
                    resellerId, curCredit, credit);
            throw new InsufficientFundsException(
                    "Insufficient credit. Current balance: " + curCredit + ", Requested: " + credit);
        }

        reseller.setCredit(newBalance);

        // Get performer info if not provided
        String performer = performedBy;
        if (performer == null) {
            performer = getCurrentUserEmail();
        }

        ResellerAddCredit resellerAddCredit = ResellerAddCredit.createDebit(
                reseller, credit, newBalance, reason, performer);

        resellerRepository.save(reseller);
        resellerAddCreditRepository.save(resellerAddCredit);

        // Audit log
        Map<String, Object> before = new HashMap<>();
        before.put("balance", curCredit);

        Map<String, Object> after = new HashMap<>();
        after.put("balance", newBalance);
        after.put("amountDeducted", credit);
        after.put("reason", reason);

        adminAuditService.logResellerCreditAction(
                ACTION_DEDUCT_RESELLER_CREDIT,
                reseller.getId(), reseller.getUser().getEmail(),
                before, after,
                String.format("Deducted %s credit from reseller %s. Reason: %s",
                        credit, reseller.getUser().getEmail(), reason));

        // Check for low balance and log warning
        if (newBalance.compareTo(LOW_BALANCE_THRESHOLD) <= 0) {
            log.warn("Reseller {} has low balance: {}", resellerId, newBalance);
        }

        log.info("Deducted reseller {} credit {}. New balance: {}", resellerId, credit, newBalance);

        return resellerViewMapper.toView(reseller);
    }

    // Only should be called when service group is removed
    public void removeServiceGroup(ServiceGroup serviceGroup) {
        List<Reseller> resellers = resellerRepository.findAll();
        resellers.forEach(reseller -> {
            reseller.getServiceGroups().remove(serviceGroup);
        });

        resellerRepository.saveAll(resellers);
    }

    public Reseller getResellerById(int id) {
        return resellerRepository.findById(id)
                .orElseThrow(() -> new NotFoundException(Reseller.class, id));
    }

    public Reseller getResellerByUser(User user) {
        return resellerRepository.findResellerByUser(user)
                .orElseThrow(() -> new InternalException("Can't find reseller"));
    }

    public ResellerLevel getResellerLevel(ResellerLevelName resellerLevelName) {
        return resellerLevelRepository.getByName(resellerLevelName);
    }

    public ResellerLevelView updateResellerLevel(int id, ResellerLevelEdit levelEdit) {
        log.info("Updating reseller level {} to {}", id, levelEdit);

        ResellerLevel level = getResellerLevel(id);
        resellerLevelEditMapper.edit(level, levelEdit);
        resellerLevelRepository.save(level);

        return resellerLevelViewMapper.toView(level);
    }

    public ResellerLevelCoefficientsView updateResellerLevelCoefficients(
            ResellerLevelCoefficientsEdit resellerLevelCoefficientsEdit) {
        log.info("Updating reseller level coefficients {}", resellerLevelCoefficientsEdit);

        ResellerLevelCoefficients resellerLevelCoefficients = getResellerLevelCoefficientsEntity();
        resellerLevelCoefficientsMapper.edit(resellerLevelCoefficients, resellerLevelCoefficientsEdit);
        resellerLevelCoefficientsRepository.save(resellerLevelCoefficients);

        log.info("Updated reseller level coefficients successfully");
        return resellerLevelCoefficientsMapper.toView(resellerLevelCoefficients);
    }

    public List<ResellerLevelView> getResellersLevels() {
        return resellerLevelRepository.findAll()
                .stream()
                .map(resellerLevelViewMapper::toView)
                .collect(Collectors.toList());
    }

    public List<ResellerCreditView> getResellersCredits() {
        return resellerRepository.findAll()
                .stream()
                .map(resellerCreditViewMapper::toView)
                .collect(Collectors.toList());
    }

    public ResellerLevelCoefficientsView getResellerLevelCoefficients() {
        ResellerLevelCoefficients resellerLevelCoefficients = getResellerLevelCoefficientsEntity();
        return resellerLevelCoefficientsMapper.toView(resellerLevelCoefficients);
    }

    public ResellerLevel getResellerLevel(int id) {
        return resellerLevelRepository.findById(id)
                .orElseThrow(() -> new NotFoundException(ResellerLevel.class, id));
    }

    public ResellerLevelCoefficients getResellerLevelCoefficientsEntity() {
        return resellerLevelCoefficientsRepository.findById(1)
                .orElseThrow(() -> new NotFoundException(ResellerLevel.class, 1));
    }

    /**
     * Gets the email of the currently authenticated user.
     * Returns "SYSTEM" if no user is authenticated (e.g., scheduled tasks).
     */
    private String getCurrentUserEmail() {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.getPrincipal() instanceof User) {
                return ((User) authentication.getPrincipal()).getEmail();
            }
        } catch (Exception e) {
            log.debug("Could not get current user email: {}", e.getMessage());
        }
        return "SYSTEM";
    }
}
