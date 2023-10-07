package com.github.vokorm

import com.gitlab.mvysny.jdbiorm.Dao
import com.gitlab.mvysny.jdbiorm.EntityMeta
import com.gitlab.mvysny.jdbiorm.spi.AbstractEntity
import jakarta.validation.ConstraintViolationException
import org.jdbi.v3.core.mapper.reflect.ColumnName

/**
 * Allows you to fetch rows of a database table, and adds useful utility methods [save]
 * and [delete].
 *
 * Automatically will try to store/update/retrieve all non-transient fields declared by this class and all superclasses.
 * To exclude fields, either mark them `transient` or [org.jdbi.v3.core.annotation.Unmappable].
 *
 * If your table has no primary key or there is other reason you don't want to use this interface, you can still use
 * the DAO methods (see [com.gitlab.mvysny.jdbiorm.DaoOfAny] for more details); you only lose the ability to [save],
 * [create] and [delete].
 *
 * Annotate the class with [com.gitlab.mvysny.jdbiorm.Table] to set SQL table name.
 *
 * ### Mapping columns
 * Use the [ColumnName] annotation to change the name of the column.
 * Please make sure to attach the annotation the field, for example
 * `@field:ColumnName("DateTime")`.
 *
 * ### Auto-generated IDs vs pre-provided IDs
 * There are generally three cases for entity ID generation:
 *
 *  * IDs generated by the database when the `INSERT` statement is executed
 *  * Natural IDs, such as a NaturalPerson with ID pre-provided by the government (social security number etc).
 *  * IDs created by the application, for example via [java.util.UUID.randomUUID]
 *
 * The [save] method is designed to work out-of-the-box only for the first case (IDs auto-generated by the database). In this
 * case, [save] emits `INSERT` when the ID is null, and `UPDATE` when the ID is not null.
 *
 * When the ID is pre-provided, you can only use [save] method to update a row in the database; using [save] to create a
 * row in the database will throw an exception. In order to create an
 * entity with a pre-provided ID, you need to use the [.create] method:
 * ```
 * NaturalPerson("12345678", "Albedo").create()
 * ```
 *
 * For entities with IDs created by the application you can make [save] work properly, by overriding the [create] method
 * as follows:
 * ```
 * override fun create(validate: Boolean) {
 *   id = UUID.randomUUID()
 *   super.create(validate)
 * }
 * ```
 * @param ID the type of the primary key. All finder methods will only accept this type of ids.
 * @author mavi
 */
public interface KEntity<ID: Any> : AbstractEntity<ID> {
    /**
     * The ID primary key.
     *
     * You can use the [ColumnName] annotation to change
     * the actual db column name - please make sure to attach the annotation the field, for example
     * `@field:ColumnName("DateTime")`.
     */
    public var id: ID?

    /**
     * Validates current entity. The Java JSR303 validation is performed by default: just add `jakarta.validation`
     * annotations to entity properties.
     *
     * Make sure to add the validation annotations to
     * fields otherwise they will be ignored. For example `@field:NotNull`.
     *
     * You can override this method to perform additional validations on the level of the entire entity.
     *
     * @throws ConstraintViolationException when validation fails.
     */
    public fun validate() {
        EntityMeta<KEntity<ID>>(javaClass).defaultValidate(this)
    }

    /**
     * Checks whether this entity is valid: calls [validate] and returns false if [ConstraintViolationException] is thrown.
     */
    public val isValid: Boolean
        get() = try {
            validate()
            true
        } catch (ex: ConstraintViolationException) {
            false
        }

    /**
     * Deletes this entity from the database. Fails if [id] is null,
     * since it is expected that the entity is already in the database.
     */
    public fun delete() {
        val id = checkNotNull(id) { "The id is null, the entity is not yet in the database" }
        Dao<KEntity<ID>, ID>(javaClass).deleteById(id)
    }

    /**
     * Always issues the database `INSERT`, even if the [id] is not null. This is useful for two cases:
     *  * When the entity has a natural ID, such as a NaturalPerson with ID pre-provided by the government (social security number etc),
     *  * ID auto-generated by the application, e.g. UUID
     *
     * It is possible to use this function with entities with IDs auto-generated by the database, but it may be simpler to
     * simply use [save].
     */
    public fun create(validate: Boolean = true) {
        if (validate) {
            validate()
        }
        EntityMeta<KEntity<ID>>(javaClass).defaultCreate(this)
    }

    /**
     * Creates a new row in a database (if [id] is null) or updates the row in a database (if [id] is not null).
     * When creating, this method simply calls the [create] method.
     *
     * It is expected that the database will generate an id for us (by sequences,
     * `auto_increment` or other means). That generated ID is then automatically stored into the [id] field.
     *
     * The bean is validated first, by calling [validate].
     * You can bypass this by setting the `validate` parameter to false, but that's not
     * recommended.
     *
     * **WARNING**: if your entity has pre-provided (natural) IDs, you must not call
     * this method with the intent to insert the entity into the database - this method will always run UPDATE and then
     * fail (since nothing has been updated since the row is not in the database yet).
     * To force create the database row, call [create].
     *
     * **INFO**: Entities with IDs created by the application can be made to work properly, by overriding [create]
     * and [create] method accordingly. See [com.gitlab.mvysny.jdbiorm.Entity] doc for more details.
     *
     * @throws IllegalStateException if the database didn't provide a new ID (upon new row creation),
     * or if there was no row (if [id] was not null).
     */
    public fun save(validate: Boolean = true) {
        if (validate) {
            validate()
        }
        if (id == null) {
            create(false) // no need to validate again
        } else {
            EntityMeta<KEntity<ID>>(javaClass).defaultSave(this)
        }
    }

    /**
     * Re-populates this entity with the up-to-date values from the database.
     * The [id] must not be null.
     * @throws IllegalStateException if the ID is null.
     */
    public fun reload() {
        checkNotNull(id) { "Invalid state: id is null" }
        EntityMeta<KEntity<*>>(javaClass).defaultReload(this)
    }
}