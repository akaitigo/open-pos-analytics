package com.akaitigo.posanalytics.domain

import io.quarkus.hibernate.orm.panache.kotlin.PanacheCompanionBase
import io.quarkus.hibernate.orm.panache.kotlin.PanacheEntityBase
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.Instant

@Entity
@Table(name = "transactions")
class TransactionEntity : PanacheEntityBase {
    companion object : PanacheCompanionBase<TransactionEntity, Long> {
        fun existsBySourceId(sourceTransactionId: String): Boolean =
            count("sourceTransactionId = ?1", sourceTransactionId) > 0
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Long? = null

    @Column(name = "source_transaction_id", nullable = false, unique = true)
    lateinit var sourceTransactionId: String

    @Column(name = "occurred_at", nullable = false)
    lateinit var occurredAt: Instant

    @Column(name = "customer_hash")
    var customerHash: String? = null

    @Column(name = "total_amount", nullable = false)
    lateinit var totalAmount: BigDecimal

    @Column(name = "item_count", nullable = false)
    var itemCount: Int = 0
}
