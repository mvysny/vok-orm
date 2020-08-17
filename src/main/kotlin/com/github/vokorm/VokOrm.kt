package com.github.vokorm

/**
 * Global configuration for vok-orm. For the JDBC configuration please see
 * [com.gitlab.mvysny.jdbiorm.JdbiOrm].
 * @author mavi
 */
public object VokOrm {
    @Volatile
    public var filterToSqlConverter: FilterToSqlConverter = DefaultFilterToSqlConverter()

    @Volatile
    public var databaseVariant: DatabaseVariant = DatabaseVariant.Unknown
}
