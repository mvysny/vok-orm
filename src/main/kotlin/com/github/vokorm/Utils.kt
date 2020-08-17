package com.github.vokorm

/**
 * Checks whether this class implements given interface [intf].
 */
public fun Class<*>.implements(intf: Class<*>): Boolean {
    require(intf.isInterface) { "$intf is not an interface" }
    return intf.isAssignableFrom(this)
}
