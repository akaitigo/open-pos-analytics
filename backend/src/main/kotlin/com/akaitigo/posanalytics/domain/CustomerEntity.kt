package com.akaitigo.posanalytics.domain

import io.quarkus.hibernate.orm.panache.kotlin.PanacheCompanionBase
import io.quarkus.hibernate.orm.panache.kotlin.PanacheEntityBase
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.Instant

@Entity
@Table(name = "customers")
class CustomerEntity : PanacheEntityBase {
    companion object : PanacheCompanionBase<CustomerEntity, String>

    @Id
    @Column(name = "customer_hash")
    lateinit var customerHash: String

    @Column(name = "first_seen_at", nullable = false)
    lateinit var firstSeenAt: Instant

    @Column(name = "last_seen_at", nullable = false)
    lateinit var lastSeenAt: Instant

    @Column(name = "visit_count", nullable = false)
    var visitCount: Int = 0

    @Column(name = "total_spent", nullable = false)
    lateinit var totalSpent: BigDecimal
}
