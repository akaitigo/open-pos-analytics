package com.akaitigo.posanalytics.domain

import io.quarkus.hibernate.orm.panache.kotlin.PanacheCompanionBase
import io.quarkus.hibernate.orm.panache.kotlin.PanacheEntityBase
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.math.BigDecimal

@Entity
@Table(name = "line_items")
class LineItemEntity : PanacheEntityBase {

    companion object : PanacheCompanionBase<LineItemEntity, Long>

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Long? = null

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "transaction_id", nullable = false)
    lateinit var transaction: TransactionEntity

    @Column(name = "product_code", nullable = false)
    lateinit var productCode: String

    @Column(name = "product_name", nullable = false)
    lateinit var productName: String

    @Column(name = "category", nullable = false)
    lateinit var category: String

    @Column(name = "quantity", nullable = false)
    var quantity: Int = 0

    @Column(name = "unit_price", nullable = false)
    lateinit var unitPrice: BigDecimal

    @Column(name = "line_amount", nullable = false)
    lateinit var lineAmount: BigDecimal
}
