package com.orbvpn.api.domain.entity;

/**
 * Type alias for Reseller entity.
 *
 * This is provided for semantic clarity - when working with the reseller-specific
 * profile data (credit, level, service groups), you can use this alias to make
 * the code more self-documenting.
 *
 * Both Reseller and ResellerProfile refer to the same entity and can be used
 * interchangeably. The actual entity definition is in Reseller.java.
 *
 * @see Reseller - The actual entity class
 */
public class ResellerProfile extends Reseller {
    // This is a type alias for Reseller
    // All functionality is inherited from Reseller
}
