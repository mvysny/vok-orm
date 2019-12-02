package com.github.vokorm

/**
 * Configures vok-orm.
 * @author mavi
 */
object VokOrm {
    @Volatile
    var filterToSqlConverter: FilterToSqlConverter = DefaultFilterToSqlConverter()
}
