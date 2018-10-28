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
 * Optional annotation which tells the underlying column name for a property, so that we can properly construct the WHERE clause (in WHERE
 * clause we can't use aliases but the actual underlying column name).
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
        db {
            if (id == null) {
                // not yet in the database, run the INSERT statement
                val fields = meta.persistedFieldDbNames - meta.idProperty.dbColumnName
                con.createQuery("insert into ${meta.databaseTableName} (${fields.joinToString()}) values (${fields.map { ":$it" }.joinToString()})", true)
                    .bindAliased(this@Entity)
                    .setColumnMappings(meta.getSql2oColumnMappings())
                    .executeUpdate()
                val key = requireNotNull(con.key) { "The database have returned null key for the created record. Have you used AUTO INCREMENT or SERIAL for primary key?" }
                @Suppress("UNCHECKED_CAST")
                id = convertID(key)
            } else {
                val fields = meta.persistedFieldDbNames - meta.idProperty.dbColumnName
                con.createQuery("update ${meta.databaseTableName} set ${fields.map { "$it = :$it" }.joinToString()} where ${meta.idProperty.dbColumnName} = :${meta.idProperty.dbColumnName}")
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
            con.createQuery("delete from ${meta.databaseTableName} where ${meta.idProperty.dbColumnName} = :id")
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
    private fun convertID(id: Any): ID = when (meta.idProperty.valueType) {
        java.lang.Integer::class.java, Integer.TYPE -> (id as Number).toInt() as ID
        java.lang.Long::class.java, java.lang.Long.TYPE -> (id as Number).toLong() as ID
        else -> meta.idProperty.valueType.cast(id) as ID
    }
}

data class EntityMeta(val entityClass: Class<out Any>) : Serializable {
    /**
     * The name of the database table backed by this entity. Defaults to [Class.getSimpleName]
     * (no conversion from `camelCase` to `hyphen_separated`) but you can annotate your class with [Table.dbname] to override
     * that.
     */
    val databaseTableName: String get() {
        val annotatedName = entityClass.getAnnotation<Table>(Table::class.java)?.dbname
        return if (annotatedName != null && annotatedName.isNotBlank()) annotatedName else entityClass.simpleName
    }
    /**
     * A list of database names of all persisted fields in this entity.
     */
    val persistedFieldDbNames: Set<String> get() = properties.map { it.dbColumnName } .toSet()

    /**
     * The `id` property as declared in the entity.
     */
    val idProperty: PropertyMeta get() = PropertyMeta(checkNotNull(entityClass.findDeclaredField("id")) { "Unexpected: entity $entityClass has no id column?" } )

    /**
     * Lists all properties in this entity. Only lists persisted properties (e.g. not annotated with [Ignore]).
     */
    val properties: Set<PropertyMeta> get() = Collections.unmodifiableSet(entityClass.persistedProperties)

    fun getProperty(property: KProperty1<*, *>): PropertyMeta = getProperty(property.name)

    fun getProperty(propertyName: String): PropertyMeta {
        val result = properties.first { it.name == propertyName }
        return checkNotNull(result) { "There is no such property $propertyName in $entityClass, available fields: ${properties.map { it.name }}" }
    }

    /**
     * Returns a map which maps from database name to the bean property name.
     */
    fun getSql2oColumnMappings(): Map<String, String> = properties.map { it.dbColumnName to it.name }.toMap()
}

/**
 * Provides meta-data for a particular property of a particular [Entity].
 * @property field the field
 */
data class PropertyMeta(val field: Field) {
    val name: String get() = this.field.name
    /**
     * The database name of given field. Defaults to [Field.name], but it can be changed via the [As] annotation.
     *
     * This column name must be used in the WHERE clauses.
     */
    val dbColumnName: String get() {
        val a = this.field.getAnnotation<As>(As::class.java)?.databaseColumnName
        return if (a == null) this.field.name else a
    }

    /**
     * The type of the value this field can take.
     */
    val valueType: Class<*> get() = this.field.type

    fun get(entity: Any): Any? {
        field.isAccessible = true
        return field.get(entity)
    }
}

private fun Class<*>.findDeclaredField(name: String): Field? {
    if (this == Object::class.java) return null
    val f = declaredFields.firstOrNull { it.name == "id" }
    if (f != null) return f
    return superclass.findDeclaredField(name)
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

private val persistedPropertiesCache: ConcurrentMap<Class<*>, Set<PropertyMeta>> = ConcurrentHashMap<Class<*>, Set<PropertyMeta>>()

/**
 * Returns the set of properties in an entity.
 */
private val <T : Any> Class<T>.persistedProperties: Set<PropertyMeta> get()
// thread-safety: this may compute the same value multiple times during high contention, this is OK
= persistedPropertiesCache.getOrPut(this) { persistedFields.map { PropertyMeta(it) } .toSet() }

/**
 * Similar to [Query.bind] but honors the [As] annotation.
 */
fun Query.bindAliased(entity: Any): Query {
    val meta = entity.javaClass.entityMeta
    meta.properties.forEach { property ->
        if (paramNameToIdxMap.containsKey(property.dbColumnName)) {
            @Suppress("UNCHECKED_CAST")
            addParameter(property.dbColumnName, property.valueType as Class<Any>, property.get(entity))
        }
    }
    return this
}
