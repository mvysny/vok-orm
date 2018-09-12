package com.github.vokorm

import org.sql2o.Query
import java.io.Serializable
import java.lang.reflect.Field
import java.lang.reflect.Modifier
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap
import javax.validation.ConstraintViolation
import javax.validation.ConstraintViolationException
import javax.validation.ValidationException
import kotlin.reflect.KProperty1

/**
 * Optional annotation which allows you to change the table name.
 * @property dbname the database table name; defaults to an empty string which will use the [Class.getSimpleName] as the table name.
 */
@Target(AnnotationTarget.CLASS)
annotation class Table(val dbname: String = "")

/**
 * Optional annotation which configures the underlying column name for a field.
 */
@Target(AnnotationTarget.FIELD)
annotation class As(val databaseColumnName: String)

/**
 * Annotate a field with this to exclude it from being mapped into a database table column.
 */
@Target(AnnotationTarget.FIELD)
annotation class Ignore

/**
 * Allows you to fetch rows of a database table, and adds useful utility methods [save]
 * and [delete].
 *
 * Automatically will try to store/update/retrieve all non-transient fields declared by this class and all superclasses; either use
 * [Transient] or [Ignore] to exclude fields.
 *
 * Note that [Sql2o] works with all pojos and does not require any annotation/interface. Thus, if your table has no primary
 * key or there is other reason you don't want to use this interface, you can still use your class with [db], you'll only
 * lose those utility methods.
 * @param ID the type of the primary key. All finder methods will only accept this type of ids.
 */
interface Entity<ID: Any> : Serializable {
    /**
     * The ID primary key. You can use the [Column] annotation to change the actual db column name.
     */
    var id: ID?

    // private since we don't want this to be exposed via e.g. Vaadin Flow.
    private val meta get() = EntityMeta(javaClass)

    /**
     * Creates a new row in a database (if [id] is null) or updates the row in a database (if [id] is not null).
     *
     * When creating, expects the [id] field to be null. It is expected that the database will generate an id for us (by sequences,
     * `auto_increment` or other means). That generated ID is then automatically stored into the [id] field.
     *
     * The bean is validated first, by calling [Entity.validate]. You can bypass this by setting [validate] to false, but that's not
     * recommended.
     */
    fun save(validate: Boolean = true) {
        if (validate) { validate() }
        validate()
        db {
            if (id == null) {
                // not yet in the database, run the INSERT statement
                val fields = meta.persistedFieldDbNames - meta.idDbname
                con.createQuery("insert into ${meta.databaseTableName} (${fields.joinToString()}) values (${fields.map { ":$it" }.joinToString()})", true)
                    .bindAliased(this@Entity)
                    .setColumnMappings(meta.getSql2oColumnMappings())
                    .executeUpdate()
                val key = requireNotNull(con.key) { "The database have returned null key for the created record. Have you used AUTO INCREMENT or SERIAL for primary key?" }
                @Suppress("UNCHECKED_CAST")
                id = convertID(key)
            } else {
                val fields = meta.persistedFieldDbNames - meta.idDbname
                con.createQuery("update ${meta.databaseTableName} set ${fields.map { "$it = :$it" }.joinToString()} where ${meta.idDbname} = :${meta.idDbname}")
                    .bindAliased(this@Entity)
                    .setColumnMappings(meta.getSql2oColumnMappings())
                    .executeUpdate()
            }
        }
    }

    /**
     * Deletes this entity from the database. Fails if [id] is null, since it is expected that the entity is already in the database.
     */
    fun delete() {
        check(id != null) { "The id is null, the entity is not yet in the database" }
        db {
            con.createQuery("delete from ${meta.databaseTableName} where ${meta.idDbname} = :id")
                .addParameter("id", id)
                .executeUpdate()
        }
    }

    /**
     * Validates current entity. By default performs the java validation: just add `javax.validation` annotations to entity properties.
     * Make sure to add the validation annotations to
     * fields (by annotating Kotlin properties as `@field:NotNull`) otherwise they will be ignored.
     *
     * You can override this method to perform additional validations on the level of the entire entity.
     * @throws javax.validation.ValidationException when validation fails.
     */
    fun validate() {
        val violations: Set<ConstraintViolation<Entity<ID>>> = VokOrm.validator.validate(this)
        if (!violations.isEmpty()) {
            throw ConstraintViolationException(violations)
        }
    }

    fun isValid() = try { validate(); true } catch (e: ValidationException) { false }

    @Suppress("UNCHECKED_CAST")
    private fun convertID(id: Any): ID = when (meta.idClass) {
        java.lang.Integer::class.java, Integer.TYPE -> (id as Number).toInt() as ID
        java.lang.Long::class.java, java.lang.Long.TYPE -> (id as Number).toLong() as ID
        else -> meta.idClass.cast(id) as ID
    }
}

data class EntityMeta(val entityClass: Class<out Any>) : Serializable {
    /**
     * The name of the database table backed by this entity. Defaults to [Class.getSimpleName]
     * (no conversion from `camelCase` to `hyphen_separated`) but you can annotate your class with [Table.dbname] to override
     * that.
     */
    val databaseTableName: String get() = entityClass.databaseTableName
    /**
     * A list of database names of all persisted fields in this entity.
     */
    val persistedFieldDbNames: Set<String> get() = entityClass.persistedFieldNames.values.toSet()

    /**
     * The database name of the ID column.
     */
    val idDbname: String get() = idField.dbname

    /**
     * The Java reflection [Field] for the `id` property as declared in the entity.
     */
    val idField: Field get() = checkNotNull(entityClass.findDeclaredField("id")) { "Unexpected: entity $entityClass has no id column?" }

    /**
     * The type of the `id` property as declared in the entity.
     */
    val idClass: Class<*> get() = idField.type

    /**
     * All fields in the entity, maps the field to the database column name.
     */
    val fields: Map<Field, String> get() = Collections.unmodifiableMap(entityClass.persistedFieldNames)

    fun propertyToField(property: KProperty1<*, *>): Field {
        val result = fields.keys.first { it.name == property.name }
        return checkNotNull(result) { "There is no such property $property in $entityClass, available fields: ${fields.keys.map { it.name }}" }
    }

    /**
     * Returns a map which maps from database name to the bean property name.
     */
    fun getSql2oColumnMappings(): Map<String, String> = fields.map { it.value to it.key.name }.toMap()
}

private fun Class<*>.findDeclaredField(name: String): Field? {
    if (this == Object::class.java) return null
    val f = declaredFields.firstOrNull { it.name == "id" }
    if (f != null) return f
    return superclass.findDeclaredField(name)
}

/**
 * Returns the name of the database table backed by this entity. Defaults to [Class.getSimpleName]
 * (no conversion from `camelCase` to `hyphen_separated`) but you can annotate your class with [Table.dbname] to override
 * that.
 */
private val Class<*>.databaseTableName: String get() {
    val annotatedName = getAnnotation(Table::class.java)?.dbname
    return if (annotatedName != null && annotatedName.isNotBlank()) annotatedName else simpleName
}

/**
 * Provides reflection utils to examine the entity metadata.
 */
val Class<*>.entityMeta: EntityMeta get() = EntityMeta(this)

private inline val Field.isTransient get() = Modifier.isTransient(modifiers)
private inline val Field.isStatic get() = Modifier.isStatic(modifiers)

private val Field.isPersisted get() = !isTransient && !isSynthetic && !isStatic && !isAnnotationPresent(Ignore::class.java) && name != "Companion"

/**
 * Lists all persisted fields.
 */
private val Class<*>.persistedFields: List<Field> get() = when {
    this == Object::class.java -> listOf()
    else -> declaredFields.filter { it.isPersisted } + superclass.persistedFields
}

private val persistedFieldNamesCache: ConcurrentMap<Class<*>, Map<Field, String>> = ConcurrentHashMap<Class<*>, Map<Field, String>>()

/**
 * The database name of given field. Defaults to [Field.name], but it can be changed via the [As] annotation.
 */
private val Field.dbname: String get() {
    val a = getAnnotation(As::class.java)?.databaseColumnName
    return if (a == null) name else a
}

/**
 * Returns the list of fields in an entity, mapped to the database name as specified by [Field.dbname].
 */
private val <T : Any> Class<T>.persistedFieldNames: Map<Field, String> get()
// thread-safety: this may compute the same value multiple times during high contention, this is OK
= persistedFieldNamesCache.getOrPut(this) { (persistedFields.associate { it to it.dbname }) }

/**
 * Similar to [Query.bind] but honors the [As] annotation.
 */
fun Query.bindAliased(entity: Any): Query {
    val meta = entity.javaClass.entityMeta
    meta.fields.forEach { field, dbname ->
        if (paramNameToIdxMap.containsKey(dbname)) {
            field.isAccessible = true
            @Suppress("UNCHECKED_CAST")
            addParameter(dbname, field.type as Class<Any>, field.get(entity))
        }
    }
    return this
}
