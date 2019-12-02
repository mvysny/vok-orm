package com.github.vokorm

/**
 * Global configuration for vok-orm. For the JDBC configuration please see
 * [com.gitlab.mvysny.jdbiorm.JdbiOrm].
 * @author mavi
 */
object VokOrm {
    @Volatile
    var filterToSqlConverter: FilterToSqlConverter = DefaultFilterToSqlConverter()

    @Volatile
    var databaseVariant: DatabaseVariant = DatabaseVariant.Unknown
}
