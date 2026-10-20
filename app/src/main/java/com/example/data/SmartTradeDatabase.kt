package com.example.data

import android.content.Context
import androidx.room.*
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "products")
data class ProductEntity(
    @PrimaryKey val key: String,
    val name: String,
    val majorUnit: String = "كيلو",
    val minorUnit: String = "",
    val factor: Double = 2.0,
    val colorBgHex: Long,
    val colorTextHex: Long,
    val borderHex: Long,
    val price: Double = 0.0,
    val stock: Double = 0.0,
    val openingStock: Double = 0.0,
    val sortOrder: Int = 0
)

@Entity(
    tableName = "customers",
    indices = [
        Index(value = ["name"], unique = true),
        Index(value = ["updatedAt"])
    ]
)
data class CustomerEntity(
    @PrimaryKey val name: String,
    val phone: String = "",
    val lastRemainingDebt: Double = 0.0,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "sales_invoices",
    indices = [
        Index(value = ["date", "timestamp"]),
        Index(value = ["date", "subtotal", "discount", "paid", "previousDebt", "remaining"]),
        Index(value = ["date"]),
        Index(value = ["customerName", "timestamp"]),
        Index(value = ["customerName"]),
        Index(value = ["invoiceNumber"]),
        Index(value = ["timestamp"])
    ]
)
data class SaleInvoiceEntity(
    @PrimaryKey val id: String,
    val invoiceNumber: String,
    val marker: String = "🔴",
    val customerName: String,
    val customerPhone: String = "",
    val subtotal: Double,
    val previousDebt: Double = 0.0,
    val isPrevDebtPaidModel: Boolean = false,
    val discount: Double = 0.0,
    val totalDue: Double,
    val paid: Double,
    val remaining: Double,
    val suppliedGoodsNote: String = "",
    val dayName: String,
    val date: String,
    val time: String,
    val timestamp: Long = System.currentTimeMillis(),
    val itemsJson: String,
    val whatsappText: String,
    val managerWhatsappText: String = ""
)

@Entity(
    tableName = "supply_batches",
    indices = [
        Index(value = ["date", "timestamp"]),
        Index(value = ["productKey", "date"]),
        Index(value = ["date", "productKey", "addedMajorEquivalent"]),
        Index(value = ["date"]),
        Index(value = ["productKey"]),
        Index(value = ["batchNumber"]),
        Index(value = ["timestamp"])
    ]
)
data class SupplyBatchEntity(
    @PrimaryKey val id: String,
    val batchNumber: String,
    val marker: String = "🟢",
    val productKey: String,
    val productName: String,
    val enteredMajor: Double,
    val enteredMinor: Double,
    val enteredDisplay: String,
    val addedMajorEquivalent: Double,
    val prevStock: Double,
    val cumulativeStock: Double,
    val majorUnit: String,
    val dayName: String,
    val date: String,
    val time: String,
    val timestamp: Long = System.currentTimeMillis(),
    val notes: String,
    val whatsappText: String
)

data class FinancialSummaryTuple(
    val matchedCount: Int = 0,
    val totalInvoicesSubtotal: Double = 0.0,
    val totalDiscountGiven: Double = 0.0,
    val totalCashReceived: Double = 0.0,
    val totalPrevDebtNotified: Double = 0.0,
    val totalInvoicesRemaining: Double = 0.0
)

/**
 * إسقاطات خفيفة الوزن (Lightweight Projections) لحسابات المخزون اللحظية دون تحميل نصوص الرسائل الضخمة في الذاكرة
 */
data class InvoiceDateItemsProjection(
    val date: String,
    val itemsJson: String
)

data class SupplyStockProjection(
    val productKey: String,
    val date: String,
    val addedMajorEquivalent: Double
)

data class SupplyAggregatedStockProjection(
    val productKey: String,
    val suppliedOnDate: Double,
    val suppliedAfterDate: Double
)

@Dao
interface SmartTradeDao {
    @Query("SELECT * FROM products ORDER BY sortOrder ASC")
    fun observeProducts(): Flow<List<ProductEntity>>

    @Query("SELECT * FROM products ORDER BY sortOrder ASC")
    suspend fun getAllProducts(): List<ProductEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProducts(products: List<ProductEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProduct(product: ProductEntity)

    @Query("DELETE FROM products WHERE `key` = :key")
    suspend fun deleteProductByKey(key: String)

    @Query("DELETE FROM products")
    suspend fun deleteAllProducts()

    @Query("SELECT * FROM customers ORDER BY updatedAt DESC LIMIT :limit")
    fun observeRecentCustomers(limit: Int = 250): Flow<List<CustomerEntity>>

    @Query("SELECT * FROM customers ORDER BY updatedAt DESC")
    suspend fun getAllCustomers(): List<CustomerEntity>

    @Query("SELECT * FROM customers ORDER BY updatedAt DESC LIMIT :limit")
    suspend fun getRecentCustomersForBackup(limit: Int = 2500): List<CustomerEntity>

    @Query("SELECT * FROM customers WHERE name = :name LIMIT 1")
    suspend fun getCustomerByName(name: String): CustomerEntity?

    @Query("DELETE FROM customers WHERE name = :name")
    suspend fun deleteCustomerByName(name: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCustomer(customer: CustomerEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCustomers(customers: List<CustomerEntity>)

    @Query("DELETE FROM customers")
    suspend fun deleteAllCustomers()

    @Query("SELECT * FROM sales_invoices WHERE customerName = :customerName")
    suspend fun getSalesInvoicesByCustomerName(customerName: String): List<SaleInvoiceEntity>

    @Query("UPDATE sales_invoices SET customerPhone = :phone WHERE customerName = :customerName AND customerPhone != :phone")
    suspend fun updateCustomerPhoneByName(customerName: String, phone: String)

    @Query("SELECT (SELECT COUNT(*) FROM sales_invoices) + (SELECT COUNT(*) FROM supply_batches)")
    fun observeTotalRecordsCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM sales_invoices")
    fun observeTotalInvoicesCount(): Flow<Int>

    @Query("SELECT * FROM sales_invoices WHERE date = :date ORDER BY timestamp ASC")
    fun observeSalesInvoicesByDate(date: String): Flow<List<SaleInvoiceEntity>>

    @Query("SELECT * FROM sales_invoices WHERE date >= :date ORDER BY timestamp ASC")
    fun observeSalesInvoicesOnOrAfterDate(date: String): Flow<List<SaleInvoiceEntity>>

    @Query("SELECT date, itemsJson FROM sales_invoices WHERE date >= :date")
    fun observeInvoiceItemsFromDateOnwards(date: String): Flow<List<InvoiceDateItemsProjection>>

    @Query(
        """
        SELECT * FROM sales_invoices
        WHERE (:filterDate = '' OR date = :filterDate)
          AND (:query = '' OR customerName LIKE '%' || :query || '%' OR invoiceNumber LIKE '%' || :query || '%' OR date LIKE '%' || :query || '%' OR dayName LIKE '%' || :query || '%')
        ORDER BY timestamp DESC
        LIMIT :limit
        """
    )
    fun observeFilteredInvoices(query: String, filterDate: String, limit: Int): Flow<List<SaleInvoiceEntity>>

    @Query(
        """
        SELECT 
            COUNT(*) AS matchedCount,
            COALESCE(ROUND(SUM(subtotal), 2), 0.0) AS totalInvoicesSubtotal,
            COALESCE(ROUND(SUM(discount), 2), 0.0) AS totalDiscountGiven,
            COALESCE(ROUND(SUM(paid), 2), 0.0) AS totalCashReceived,
            COALESCE(ROUND(SUM(previousDebt), 2), 0.0) AS totalPrevDebtNotified,
            COALESCE(ROUND(SUM(remaining), 2), 0.0) AS totalInvoicesRemaining
        FROM sales_invoices
        WHERE (:filterDate = '' OR date = :filterDate)
          AND (:query = '' OR customerName LIKE '%' || :query || '%' OR invoiceNumber LIKE '%' || :query || '%' OR date LIKE '%' || :query || '%' OR dayName LIKE '%' || :query || '%')
        """
    )
    fun observeFilteredFinancialSummary(query: String, filterDate: String): Flow<FinancialSummaryTuple>

    @Query("SELECT * FROM sales_invoices ORDER BY timestamp DESC")
    suspend fun getAllSalesInvoices(): List<SaleInvoiceEntity>

    @Query("SELECT * FROM sales_invoices ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentSalesInvoicesForBackup(limit: Int = 5000): List<SaleInvoiceEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSaleInvoice(invoice: SaleInvoiceEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSaleInvoices(invoices: List<SaleInvoiceEntity>)

    @Query("DELETE FROM sales_invoices WHERE id = :id")
    suspend fun deleteSaleInvoiceById(id: String)

    @Query("DELETE FROM sales_invoices")
    suspend fun deleteAllSalesInvoices()

    @Query("SELECT * FROM supply_batches ORDER BY timestamp DESC LIMIT :limit")
    fun observeRecentSupplies(limit: Int = 300): Flow<List<SupplyBatchEntity>>

    @Query("SELECT * FROM supply_batches WHERE date = :date ORDER BY timestamp ASC")
    fun observeSuppliesByDate(date: String): Flow<List<SupplyBatchEntity>>

    @Query("SELECT * FROM supply_batches WHERE date >= :date ORDER BY timestamp ASC")
    fun observeSuppliesOnOrAfterDate(date: String): Flow<List<SupplyBatchEntity>>

    @Query("SELECT productKey, date, addedMajorEquivalent FROM supply_batches WHERE date >= :date")
    fun observeSupplyStockFromDateOnwards(date: String): Flow<List<SupplyStockProjection>>

    @Query(
        """
        SELECT 
            productKey,
            COALESCE(SUM(CASE WHEN date = :date THEN addedMajorEquivalent ELSE 0.0 END), 0.0) AS suppliedOnDate,
            COALESCE(SUM(CASE WHEN date > :date THEN addedMajorEquivalent ELSE 0.0 END), 0.0) AS suppliedAfterDate
        FROM supply_batches
        WHERE date >= :date
        GROUP BY productKey
        """
    )
    fun observeAggregatedSupplyStockFromDateOnwards(date: String): Flow<List<SupplyAggregatedStockProjection>>

    @Query("SELECT * FROM supply_batches ORDER BY timestamp DESC")
    suspend fun getAllSupplies(): List<SupplyBatchEntity>

    @Query("SELECT * FROM supply_batches ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentSuppliesForBackup(limit: Int = 3000): List<SupplyBatchEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSupplyBatch(batch: SupplyBatchEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSupplyBatches(batches: List<SupplyBatchEntity>)

    @Query("DELETE FROM supply_batches WHERE id = :id")
    suspend fun deleteSupplyBatchById(id: String)

    @Query("DELETE FROM supply_batches")
    suspend fun deleteAllSupplyBatches()

    @Query("SELECT DISTINCT date FROM sales_invoices UNION SELECT DISTINCT date FROM supply_batches ORDER BY date DESC LIMIT 365")
    fun observeActiveDates(): Flow<List<String>>
}

@Database(
    entities = [
        ProductEntity::class,
        CustomerEntity::class,
        SaleInvoiceEntity::class,
        SupplyBatchEntity::class
    ],
    version = 6,
    exportSchema = false
)
abstract class SmartTradeDatabase : RoomDatabase() {
    abstract fun tradeDao(): SmartTradeDao

    companion object {
        @Volatile
        private var INSTANCE: SmartTradeDatabase? = null

        private val MIGRATION_5_6 = object : androidx.room.migration.Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_sales_invoices_date_subtotal_discount_paid_previousDebt_remaining` ON `sales_invoices` (`date`, `subtotal`, `discount`, `paid`, `previousDebt`, `remaining`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_supply_batches_date_productKey_addedMajorEquivalent` ON `supply_batches` (`date`, `productKey`, `addedMajorEquivalent`)"
                )
            }
        }

        fun getInstance(context: Context): SmartTradeDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    SmartTradeDatabase::class.java,
                    "smart_trade_ledger_v3.db"
                )
                    .setJournalMode(JournalMode.WRITE_AHEAD_LOGGING)
                    .addMigrations(MIGRATION_5_6)
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .addCallback(object : Callback() {
                        override fun onOpen(db: SupportSQLiteDatabase) {
                            super.onOpen(db)
                            try {
                                db.execSQL("PRAGMA synchronous = NORMAL;")
                                db.execSQL("PRAGMA temp_store = MEMORY;")
                                db.execSQL("PRAGMA cache_size = -16000;")
                                db.execSQL("PRAGMA mmap_size = 67108864;")
                            } catch (_: Exception) {
                            }
                        }
                    })
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

fun getInitialPaperCommodities(): List<ProductEntity> = listOf(
    ProductEntity("khadra", "خضراء", "كيلو", "", 2.0, 0xFFDCFCE7, 0xFF15803D, 0xFF86EFAC, 0.0, 0.0, 0.0, 1),
    ProductEntity("khususi", "خصوصي", "كيلو", "", 2.0, 0xFFFEF3C7, 0xFF92400E, 0xFFFDE68A, 0.0, 0.0, 0.0, 2),
    ProductEntity("shabah_white", "شبح بيضاء", "كيلو", "", 2.0, 0xFFFEF3C7, 0xFF854D0E, 0xFFFDE047, 0.0, 0.0, 0.0, 3),
    ProductEntity("shabah_yellow", "شبح صفراء", "كيلو", "", 2.0, 0xFFFEF08A, 0xFF713F12, 0xFFFACC15, 0.0, 0.0, 0.0, 4),
    ProductEntity("aidi", "عايدي", "كيلو", "", 2.0, 0xFFF8FAFC, 0xFF334155, 0xFFCBD5E1, 0.0, 0.0, 0.0, 5),
    ProductEntity("khususi_yellow", "خصوصي صفراء", "كيلو", "", 2.0, 0xFFFDE047, 0xFF713F12, 0xFFCA8A04, 0.0, 0.0, 0.0, 6),
    ProductEntity("mumtaz", "ممتاز", "كيلو", "", 2.0, 0xFFD1FAE5, 0xFF065F46, 0xFF6EE7B7, 0.0, 0.0, 0.0, 7),
    ProductEntity("sawda_asli", "سوداء أصلي", "كيلو", "", 2.0, 0xFFFED7AA, 0xFF9A3412, 0xFFFB923C, 0.0, 0.0, 0.0, 8),
    ProductEntity("sawda_ahmed", "سوداء أحمد قاسم", "كيلو", "", 2.0, 0xFFFED7AA, 0xFF7C2D12, 0xFFF97316, 0.0, 0.0, 0.0, 9),
    ProductEntity("sawda_shaibani", "سوداء شيباني", "كيلو", "", 2.0, 0xFFFED7AA, 0xFF7C2D12, 0xFFEA580C, 0.0, 0.0, 0.0, 10),
    ProductEntity("jabri", "الجابري", "كيلو", "", 2.0, 0xFFF5EBE0, 0xFF573D1C, 0xFFD5BDAF, 0.0, 0.0, 0.0, 11),
    ProductEntity("h1", "ح1", "كيلو", "", 2.0, 0xFFE6CCB2, 0xFF43281C, 0xFFB08968, 0.0, 0.0, 0.0, 12),
    ProductEntity("h2", "ح2", "كيلو", "", 2.0, 0xFFE6CCB2, 0xFF43281C, 0xFFB08968, 0.0, 0.0, 0.0, 13),
    ProductEntity("salami", "سلامي", "كيلو", "", 2.0, 0xFFE6CCB2, 0xFF43281C, 0xFFB08968, 0.0, 0.0, 0.0, 14),
    ProductEntity("b", "B", "كيلو", "", 2.0, 0xFFE6CCB2, 0xFF43281C, 0xFFB08968, 0.0, 0.0, 0.0, 15),
    ProductEntity("h", "H", "كيلو", "", 2.0, 0xFFE6CCB2, 0xFF43281C, 0xFFB08968, 0.0, 0.0, 0.0, 16)
)
