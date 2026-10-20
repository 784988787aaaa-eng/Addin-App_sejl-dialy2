package com.example.ui

import android.app.Application
import android.content.Context
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import com.example.data.*
import com.example.domain.DailyFinancialTotals
import com.example.domain.LedgerCalculator
import com.example.domain.ProductDailyStockStats
import com.example.util.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * الـ ViewModel الرشيق والمُهيأ لملايين السجلات ومنع أي وميض أو بطء في جميع الشاشات والهواتف
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SmartTradeViewModel(application: Application) : AndroidViewModel(application) {

    private val db = SmartTradeDatabase.getInstance(application)
    private val dao = db.tradeDao()
    private val prefs = application.getSharedPreferences("smart_trade_prefs_v3", Context.MODE_PRIVATE)
    private var autoBackupJob: Job? = null

    // تهيئة أولية فورية للأصناف الـ 16 لمنع أي وميض أو قفزة في الواجهة عند فتح التطبيق
    val products: StateFlow<List<ProductEntity>> = dao.observeProducts()
        .map { list -> list.ifEmpty { getInitialPaperCommodities() } }
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.Eagerly, getInitialPaperCommodities())

    val customers: StateFlow<List<CustomerEntity>> = dao.observeRecentCustomers(250)
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val totalRecordsCount: StateFlow<Int> = dao.observeTotalRecordsCount()
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val activeDates: StateFlow<List<String>> = dao.observeActiveDates()
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _activeTab = MutableStateFlow(0)
    val activeTab: StateFlow<Int> = _activeTab.asStateFlow()

    private val _selectedSheetDate = MutableStateFlow(getCurrentDateTimeArabic().date)
    val selectedSheetDate: StateFlow<String> = _selectedSheetDate.asStateFlow()

    // استعلام مفهرس O(log N) لفواتير اليوم المحدد حصراً (دون سحب تواريخ أخرى للذاكرة)
    val sheetDayInvoices: StateFlow<List<SaleInvoiceEntity>> = _selectedSheetDate
        .flatMapLatest { date -> dao.observeSalesInvoicesByDate(date) }
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val sheetDaySupplies: StateFlow<List<SupplyBatchEntity>> = _selectedSheetDate
        .flatMapLatest { date -> dao.observeSuppliesByDate(date) }
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // الملخص المالي اليومي المحسوب في الخلفية عبر المحرك المحاسبي الموحد
    val sheetFinancialTotals: StateFlow<DailyFinancialTotals> = sheetDayInvoices
        .map { dayInvs -> LedgerCalculator.computeDailyFinancialTotals(dayInvs) }
        .flowOn(Dispatchers.Default)
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            DailyFinancialTotals(0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0)
        )

    // إحصائيات المخزون اليومية المحسوبة في الخلفية عبر الإسقاطات الخفيفة وتجميعات SQL
    val sheetStockStats: StateFlow<Map<String, ProductDailyStockStats>> = _selectedSheetDate
        .flatMapLatest { date ->
            combine(
                products,
                dao.observeInvoiceItemsFromDateOnwards(date),
                dao.observeAggregatedSupplyStockFromDateOnwards(date)
            ) { prods, invProjections, supAggregations ->
                LedgerCalculator.computeDailyStockStatsFromAggregatedSupplies(
                    targetDate = date,
                    products = prods,
                    invoiceProjectionsFromDateOnwards = invProjections,
                    aggregatedSupplies = supAggregations
                )
            }
        }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    private val _suppliesPageLimit = MutableStateFlow(250)
    val recentSupplies: StateFlow<List<SupplyBatchEntity>> = _suppliesPageLimit
        .flatMapLatest { limit -> dao.observeRecentSupplies(limit) }
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _salesCounter = MutableStateFlow(prefs.getInt("sales_counter", 1001))
    val salesCounter: StateFlow<Int> = _salesCounter.asStateFlow()

    private val _supplyCounter = MutableStateFlow(prefs.getInt("supply_counter", 1001))
    val supplyCounter: StateFlow<Int> = _supplyCounter.asStateFlow()

    private val _connectedAccount = MutableStateFlow(prefs.getString("google_oauth_email_v2", "") ?: "")
    val connectedAccount: StateFlow<String> = _connectedAccount.asStateFlow()

    private val _connectedAccountName = MutableStateFlow(prefs.getString("google_oauth_name_v2", "") ?: "")
    val connectedAccountName: StateFlow<String> = _connectedAccountName.asStateFlow()

    private val _connectedAccountPhoto = MutableStateFlow(prefs.getString("google_oauth_photo_v2", "") ?: "")
    val connectedAccountPhoto: StateFlow<String> = _connectedAccountPhoto.asStateFlow()

    private val _lastAutoBackupTime = MutableStateFlow(prefs.getString("last_backup_time", "لم يتم بعد") ?: "لم يتم بعد")
    val lastAutoBackupTime: StateFlow<String> = _lastAutoBackupTime.asStateFlow()

    private val _lastCloudBackupTime = MutableStateFlow(prefs.getString("last_cloud_backup_time", "لم يرفع بعد") ?: "لم يرفع بعد")
    val lastCloudBackupTime: StateFlow<String> = _lastCloudBackupTime.asStateFlow()

    private val _lastCloudBackupSize = MutableStateFlow(prefs.getString("last_cloud_backup_size", "—") ?: "—")
    val lastCloudBackupSize: StateFlow<String> = _lastCloudBackupSize.asStateFlow()

    private val _autoCloudSyncEnabled = MutableStateFlow(prefs.getBoolean("auto_cloud_sync_enabled", true))
    val autoCloudSyncEnabled: StateFlow<Boolean> = _autoCloudSyncEnabled.asStateFlow()

    private val _customWebClientId = MutableStateFlow(
        prefs.getString("custom_web_client_id", "")?.takeIf { it.isNotBlank() }
            ?: (try {
                com.example.BuildConfig.GOOGLE_WEB_CLIENT_ID.trim()
                    .takeIf { it.endsWith(".apps.googleusercontent.com") } ?: ""
            } catch (_: Throwable) { "" })
    )
    val customWebClientId: StateFlow<String> = _customWebClientId.asStateFlow()

    private val _managerPhone = MutableStateFlow(prefs.getString("manager_phone", "") ?: "")
    val managerPhone: StateFlow<String> = _managerPhone.asStateFlow()

    private val _currency = MutableStateFlow(prefs.getString("currency", "ر.ي") ?: "ر.ي")
    val currency: StateFlow<String> = _currency.asStateFlow()

    private val _userName = MutableStateFlow(prefs.getString("user_name", "") ?: "")
    val userName: StateFlow<String> = _userName.asStateFlow()

    private val _customerNameInput = MutableStateFlow("")
    val customerNameInput: StateFlow<String> = _customerNameInput.asStateFlow()

    private val _customerPhoneInput = MutableStateFlow("")
    val customerPhoneInput: StateFlow<String> = _customerPhoneInput.asStateFlow()

    private val _selectedProductKey = MutableStateFlow("khadra")
    val selectedProductKey: StateFlow<String> = _selectedProductKey.asStateFlow()

    private val _majorQtyInput = MutableStateFlow("")
    val majorQtyInput: StateFlow<String> = _majorQtyInput.asStateFlow()

    private val _minorQtyInput = MutableStateFlow("")
    val minorQtyInput: StateFlow<String> = _minorQtyInput.asStateFlow()

    private val _unitPriceInput = MutableStateFlow("")
    val unitPriceInput: StateFlow<String> = _unitPriceInput.asStateFlow()

    private val _cartItems = MutableStateFlow<List<CartItemUi>>(emptyList())
    val cartItems: StateFlow<List<CartItemUi>> = _cartItems.asStateFlow()

    private val _previousDebtInput = MutableStateFlow("")
    val previousDebtInput: StateFlow<String> = _previousDebtInput.asStateFlow()

    private val _isPrevDebtPaidModel = MutableStateFlow(false)
    val isPrevDebtPaidModel: StateFlow<Boolean> = _isPrevDebtPaidModel.asStateFlow()

    private val _discountInput = MutableStateFlow("")
    val discountInput: StateFlow<String> = _discountInput.asStateFlow()

    private val _paidInput = MutableStateFlow("")
    val paidInput: StateFlow<String> = _paidInput.asStateFlow()

    private val _lastSavedInvoice = MutableStateFlow<SaleInvoiceEntity?>(null)
    val lastSavedInvoice: StateFlow<SaleInvoiceEntity?> = _lastSavedInvoice.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            if (dao.getAllProducts().isEmpty()) {
                dao.upsertProducts(getInitialPaperCommodities())
            }
            if (!prefs.getBoolean("direct_msg_migrated_v6", false)) {
                TradeRepositoryOperations.upgradeLegacyMessagesToDirectFormat(dao, _currency.value, _userName.value)
                prefs.edit().putBoolean("direct_msg_migrated_v6", true).apply()
            }
            try {
                val signedIn = GoogleDriveCloudBackupManager.getCurrentSignedInProfile(application)
                if (signedIn != null && signedIn.isConnected) {
                    updateConnectedGoogleProfile(signedIn)
                }
            } catch (_: Exception) {}
        }
    }

    fun observeFilteredInvoicesPaged(query: String, filterDate: String, limit: Int): Flow<List<SaleInvoiceEntity>> =
        dao.observeFilteredInvoices(query.trim(), filterDate.trim(), limit).flowOn(Dispatchers.IO)

    fun observeFilteredFinancialSummary(query: String, filterDate: String): Flow<FinancialSummaryTuple> =
        dao.observeFilteredFinancialSummary(query.trim(), filterDate.trim()).flowOn(Dispatchers.IO)

    fun loadMoreSupplies() { _suppliesPageLimit.value += 250 }

    fun setActiveTab(tab: Int) { _activeTab.value = tab }
    fun setSelectedSheetDate(date: String) { _selectedSheetDate.value = date }
    fun shiftSheetDate(daysDelta: Int) { _selectedSheetDate.value = shiftDateString(_selectedSheetDate.value, daysDelta) }

    fun setCustomerNameInput(name: String) {
        _customerNameInput.value = name
        val trimmed = name.trim()
        val matched = customers.value.find { it.name.equals(trimmed, ignoreCase = true) }
        _customerPhoneInput.value = matched?.phone.orEmpty()
    }

    fun selectCustomerSuggestion(customer: CustomerEntity) {
        _customerNameInput.value = customer.name
        _customerPhoneInput.value = customer.phone
    }

    fun setCustomerPhoneInput(phone: String) {
        _customerPhoneInput.value = ContactPickerHelper.normalizePhoneDigits(phone)
    }

    fun onCustomerContactPicked(context: Context, pickedName: String, pickedPhone: String) {
        val cleanName = pickedName.trim()
        val cleanPhone = ContactPickerHelper.normalizePhoneDigits(pickedPhone)
        if (cleanName.isNotBlank()) {
            _customerNameInput.value = cleanName
        }
        _customerPhoneInput.value = cleanPhone
        if (cleanName.isNotBlank()) {
            viewModelScope.launch(Dispatchers.IO) {
                db.withTransaction {
                    TradeRepositoryOperations.savePickedCustomerContact(dao, cleanName, cleanPhone)
                }
                scheduleSilentAutoBackup()
            }
            Toast.makeText(
                context,
                if (cleanPhone.isNotBlank()) "تم اختيار ($cleanName) وحفظ رقمه تلقائياً ✓" else "تم اختيار ($cleanName) ✓",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    fun selectProduct(product: ProductEntity) {
        val currentMaj = _majorQtyInput.value.toDoubleOrNull() ?: 0.0
        val currentMin = _minorQtyInput.value.toDoubleOrNull() ?: 0.0
        if (product.key != _selectedProductKey.value && (currentMaj > 0.0 || currentMin > 0.0)) {
            addOrUpdateCurrentItemInCart(autoDefaultToOne = false)
        }
        _selectedProductKey.value = product.key
        _unitPriceInput.value = if (product.price > 0) product.price.cleanNum() else ""
    }

    fun setMajorQtyInput(value: String) { _majorQtyInput.value = value }
    fun setMinorQtyInput(value: String) { _minorQtyInput.value = value }

    fun addQuickMajorQty(delta: Double) {
        val next = LedgerCalculator.addStock(_majorQtyInput.value.toDoubleOrNull() ?: 0.0, delta).coerceAtLeast(0.0)
        _majorQtyInput.value = if (next > 0) next.cleanNum() else ""
    }

    fun addQuickMinorQty(delta: Double) {
        val next = LedgerCalculator.addStock(_minorQtyInput.value.toDoubleOrNull() ?: 0.0, delta).coerceAtLeast(0.0)
        _minorQtyInput.value = if (next > 0) next.cleanNum() else ""
    }

    fun setUnitPriceInput(value: String) {
        _unitPriceInput.value = value
        val pVal = value.toDoubleOrNull()?.let { LedgerCalculator.roundMoney(it) }
        val key = _selectedProductKey.value
        if (pVal != null && pVal > 0.0) {
            val prod = products.value.find { it.key == key }
            if (prod != null && prod.price != pVal) {
                viewModelScope.launch(Dispatchers.IO) { dao.upsertProduct(prod.copy(price = pVal)) }
            }
        }
    }

    fun setPreviousDebtInput(value: String) {
        _previousDebtInput.value = value
        _isPrevDebtPaidModel.value = (value.toDoubleOrNull() ?: 0.0) > 0.0
    }

    fun applyCashFullPaymentModel(netInvoiceSubtotal: Double) {
        _isPrevDebtPaidModel.value = false
        val total = LedgerCalculator.roundMoney(netInvoiceSubtotal.coerceAtLeast(0.0))
        _paidInput.value = if (total > 0) total.cleanNum() else "0"
    }

    fun setDiscountInput(value: String) { _discountInput.value = value }
    fun setPaidInput(value: String) { _paidInput.value = value }

    fun saveManagerPhoneQuickly(phone: String) {
        val clean = phone.trim()
        _managerPhone.value = clean
        prefs.edit().putString("manager_phone", clean).apply()
    }

    fun updateProductDetails(
        product: ProductEntity, newName: String, newMajorUnit: String, newMinorUnit: String,
        newFactor: Double, newPrice: Double, newStock: Double
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val updated = product.copy(
                name = newName.trim().ifBlank { product.name },
                majorUnit = newMajorUnit.trim().ifBlank { "كيلو" },
                minorUnit = newMinorUnit.trim(),
                factor = if (newFactor > 0) newFactor else 2.0,
                price = LedgerCalculator.roundMoney(newPrice.coerceAtLeast(0.0)),
                stock = LedgerCalculator.roundStock(newStock)
            )
            dao.upsertProduct(updated)
            if (_selectedProductKey.value == updated.key) {
                _unitPriceInput.value = if (updated.price > 0) updated.price.cleanNum() else ""
            }
            scheduleSilentAutoBackup()
        }
    }

    fun addNewCustomProduct(name: String, majorUnit: String, minorUnit: String, factor: Double, price: Double, stock: Double) {
        val cleanName = name.trim()
        if (cleanName.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            val key = "custom_${System.currentTimeMillis()}"
            val sortOrder = (dao.getAllProducts().maxOfOrNull { it.sortOrder } ?: 16) + 1
            val cleanPrice = LedgerCalculator.roundMoney(price.coerceAtLeast(0.0))
            val cleanStock = LedgerCalculator.roundStock(stock)
            val newProd = ProductEntity(
                key = key, name = cleanName, majorUnit = majorUnit.trim().ifBlank { "كيلو" },
                minorUnit = minorUnit.trim(), factor = if (factor > 0) factor else 2.0,
                colorBgHex = 0xFFE0F2FE, colorTextHex = 0xFF0369A1, borderHex = 0xFF7DD3FC,
                price = cleanPrice, stock = cleanStock, openingStock = cleanStock, sortOrder = sortOrder
            )
            dao.upsertProduct(newProd)
            _selectedProductKey.value = key
            _unitPriceInput.value = if (cleanPrice > 0) cleanPrice.cleanNum() else ""
            scheduleSilentAutoBackup()
        }
    }

    fun deleteProduct(context: Context, product: ProductEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.deleteProductByKey(product.key)
            _cartItems.value = _cartItems.value.filterNot { it.productKey == product.key }
            val remainingProducts = dao.getAllProducts()
            if (_selectedProductKey.value == product.key) {
                val fallback = remainingProducts.firstOrNull()
                _selectedProductKey.value = fallback?.key ?: "khadra"
                _unitPriceInput.value = if ((fallback?.price ?: 0.0) > 0) fallback!!.price.cleanNum() else ""
                _majorQtyInput.value = ""
                _minorQtyInput.value = ""
            }
            scheduleSilentAutoBackup()
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "تم حذف الصنف (${product.name}) بنجاح ✓", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun getEffectiveCartItems(
        cart: List<CartItemUi>, allProducts: List<ProductEntity>, selectedKey: String, majorStr: String, minorStr: String, priceStr: String
    ): List<CartItemUi> = CartCalculatorHelper.getEffectiveCartItems(cart, allProducts, selectedKey, majorStr, minorStr, priceStr)

    fun addOrUpdateCurrentItemInCart(autoDefaultToOne: Boolean = true) {
        val prod = products.value.find { it.key == _selectedProductKey.value } ?: products.value.firstOrNull() ?: return
        var maj = _majorQtyInput.value.toDoubleOrNull() ?: 0.0
        val min = if (prod.minorUnit.isNotBlank()) (_minorQtyInput.value.toDoubleOrNull() ?: 0.0) else 0.0
        if (maj <= 0.0 && min <= 0.0) {
            if (autoDefaultToOne) maj = 1.0 else return
        }
        val price = LedgerCalculator.roundMoney(_unitPriceInput.value.toDoubleOrNull() ?: prod.price)
        if (price > 0.0 && price != prod.price) {
            viewModelScope.launch(Dispatchers.IO) { dao.upsertProduct(prod.copy(price = price)) }
        }
        _cartItems.value = CartCalculatorHelper.mergeItemIntoCartList(_cartItems.value, prod, maj, min, price)
        _majorQtyInput.value = ""
        _minorQtyInput.value = ""
    }

    fun removeCartItem(productKey: String) {
        _cartItems.value = _cartItems.value.filterNot { it.productKey == productKey }
    }

    fun clearCartAndResetPos() {
        _cartItems.value = emptyList()
        _customerNameInput.value = ""
        _customerPhoneInput.value = ""
        _majorQtyInput.value = ""
        _minorQtyInput.value = ""
        _previousDebtInput.value = ""
        _isPrevDebtPaidModel.value = false
        _discountInput.value = ""
        _paidInput.value = ""
    }

    fun saveAndShareInvoiceImmediately(context: Context, destination: InvoiceShareDestination) {
        if ((_majorQtyInput.value.toDoubleOrNull() ?: 0.0) > 0.0 || (_minorQtyInput.value.toDoubleOrNull() ?: 0.0) > 0.0) {
            addOrUpdateCurrentItemInCart(autoDefaultToOne = false)
        }
        val currentCart = _cartItems.value
        val prevDebt = _previousDebtInput.value.toDoubleOrNull() ?: 0.0
        val discount = _discountInput.value.toDoubleOrNull() ?: 0.0
        val paid = _paidInput.value.toDoubleOrNull() ?: 0.0
        val custName = _customerNameInput.value.trim().ifBlank { "عميل نقدي" }
        val custPhone = _customerPhoneInput.value.trim()

        if (currentCart.isEmpty() && prevDebt <= 0.0 && paid <= 0.0) {
            Toast.makeText(context, "أدخل كمية الصنف أو المبلغ أولاً", Toast.LENGTH_SHORT).show()
            return
        }

        val dt = getCurrentDateTimeArabic()
        val invNum = "A${_salesCounter.value}"
        val nextCounter = _salesCounter.value + 1
        _salesCounter.value = nextCounter
        prefs.edit().putInt("sales_counter", nextCounter).apply()

        val acc = LedgerCalculator.computeUnifiedInvoiceAccounting(
            rawItemsTotal = CartCalculatorHelper.sumCartTotals(currentCart),
            discount = discount,
            paidCash = paid,
            previousDebtPaid = prevDebt
        )
        val cur = _currency.value
        val uName = _userName.value

        viewModelScope.launch(Dispatchers.IO) {
            val invoiceEntity = db.withTransaction {
                TradeRepositoryOperations.createAndPersistSaleInvoice(
                    dao = dao,
                    invNum = invNum,
                    dt = dt,
                    custName = custName,
                    custPhone = custPhone,
                    currentCart = currentCart,
                    netInvoiceTotal = acc.netInvoiceSubtotal,
                    prevDebt = acc.previousDebtPaid,
                    discount = acc.discount,
                    paid = acc.paidCash,
                    remaining = acc.remainingOnInvoice,
                    currency = cur,
                    userName = uName
                )
            }
            // فتح الواتساب أو المشاركة فوراً دون انتظار النسخ الاحتياطي الخلفي
            withContext(Dispatchers.Main) {
                _lastSavedInvoice.value = invoiceEntity
                _selectedSheetDate.value = dt.date
                clearCartAndResetPos()
                when (destination) {
                    InvoiceShareDestination.CUSTOMER_WHATSAPP -> openWhatsAppDirectOrShare(context, invoiceEntity.whatsappText, invoiceEntity.customerPhone, uName)
                    InvoiceShareDestination.MANAGER_WHATSAPP -> openWhatsAppDirectOrShare(context, invoiceEntity.managerWhatsappText, _managerPhone.value, uName)
                    InvoiceShareDestination.PDF_SHARE -> {
                        viewModelScope.launch(Dispatchers.IO) {
                            val pdfFile = PdfAndBackupHelper.generateInvoicePdfFile(context, invoiceEntity, cur, uName)
                            if (pdfFile != null) {
                                withContext(Dispatchers.Main) {
                                    PdfAndBackupHelper.sharePdfFile(context, pdfFile, "فاتورة $invNum - $custName")
                                }
                            }
                        }
                    }
                }
            }
            scheduleSilentAutoBackup()
        }
    }

    fun saveSupplyBatchToManagerWhatsApp(context: Context, productKey: String, majorQty: Double, minorQty: Double, notes: String) {
        val prod = products.value.find { it.key == productKey } ?: return
        val cleanMin = if (prod.minorUnit.isNotBlank()) minorQty else 0.0
        if (majorQty <= 0.0 && cleanMin <= 0.0) {
            Toast.makeText(context, "أدخل الكمية الموردة أولاً", Toast.LENGTH_SHORT).show()
            return
        }
        val dt = getCurrentDateTimeArabic()
        val batchNum = "B${_supplyCounter.value}"
        val nextSup = _supplyCounter.value + 1
        _supplyCounter.value = nextSup
        prefs.edit().putInt("supply_counter", nextSup).apply()
        val uName = _userName.value

        viewModelScope.launch(Dispatchers.IO) {
            val batch = db.withTransaction {
                TradeRepositoryOperations.createAndPersistSupplyBatch(dao, prod, batchNum, dt, majorQty, cleanMin, notes, uName)
            }
            withContext(Dispatchers.Main) {
                _selectedSheetDate.value = dt.date
                openWhatsAppDirectOrShare(context, batch.whatsappText, _managerPhone.value, uName)
            }
            scheduleSilentAutoBackup()
        }
    }

    fun updateInvoiceRecord(invoice: SaleInvoiceEntity, newCustomerName: String, newPaid: Double, newPrevDebt: Double, newDiscount: Double) {
        viewModelScope.launch(Dispatchers.IO) {
            db.withTransaction {
                TradeRepositoryOperations.updateExistingSaleInvoice(
                    dao, invoice, newCustomerName, newPaid, newPrevDebt, newDiscount, _currency.value, _userName.value
                )
            }
            scheduleSilentAutoBackup()
        }
    }

    fun updateCustomerContactRecord(invoice: SaleInvoiceEntity, newCustomerName: String, newCustomerPhone: String) {
        viewModelScope.launch(Dispatchers.IO) {
            db.withTransaction {
                TradeRepositoryOperations.updateCustomerContactInfo(
                    dao, invoice, newCustomerName, newCustomerPhone, _currency.value, _userName.value
                )
            }
            val currentLast = _lastSavedInvoice.value
            if (currentLast != null && (currentLast.id == invoice.id || currentLast.customerName == invoice.customerName)) {
                _lastSavedInvoice.value = currentLast.copy(
                    customerName = newCustomerName.trim().ifBlank { invoice.customerName },
                    customerPhone = ContactPickerHelper.normalizePhoneDigits(newCustomerPhone)
                )
            }
            scheduleSilentAutoBackup()
        }
    }

    fun updateSupplyRecord(batch: SupplyBatchEntity, newMajorQty: Double, newMinorQty: Double, newNotes: String) {
        viewModelScope.launch(Dispatchers.IO) {
            db.withTransaction {
                TradeRepositoryOperations.updateExistingSupplyBatch(dao, batch, newMajorQty, newMinorQty, newNotes, _userName.value)
            }
            scheduleSilentAutoBackup()
        }
    }

    fun deleteInvoice(invoice: SaleInvoiceEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            db.withTransaction {
                TradeRepositoryOperations.deleteInvoiceAndRestoreStock(dao, invoice)
            }
            scheduleSilentAutoBackup()
        }
    }

    fun deleteSupplyBatch(batch: SupplyBatchEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            db.withTransaction {
                TradeRepositoryOperations.deleteSupplyAndRevertStock(dao, batch)
            }
            scheduleSilentAutoBackup()
        }
    }

    fun resetAllApplicationData(
        context: Context,
        startSalesCounter: Int = 1001,
        startSupplyCounter: Int = 1001
    ) {
        val validSalesStart = startSalesCounter.coerceAtLeast(1)
        val validSupplyStart = startSupplyCounter.coerceAtLeast(1)
        viewModelScope.launch(Dispatchers.IO) {
            db.withTransaction {
                TradeRepositoryOperations.resetAllDatabaseData(dao)
            }
            _salesCounter.value = validSalesStart
            _supplyCounter.value = validSupplyStart
            _lastSavedInvoice.value = null
            _selectedSheetDate.value = getCurrentDateTimeArabic().date
            prefs.edit()
                .putInt("sales_counter", validSalesStart)
                .putInt("supply_counter", validSupplyStart)
                .apply()
            withContext(Dispatchers.Main) {
                clearCartAndResetPos()
                Toast.makeText(
                    context,
                    "تمت تهيئة النظام بنجاح ✓ (البيع يبدأ من A$validSalesStart والتوريد من B$validSupplyStart)",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    fun setConnectedAccount(email: String) {
        _connectedAccount.value = email
        prefs.edit().putString("google_oauth_email_v2", email).apply()
        scheduleSilentAutoBackup()
    }

    fun updateConnectedGoogleProfile(profile: ConnectedGoogleAccountProfile) {
        _connectedAccount.value = profile.email
        _connectedAccountName.value = profile.displayName
        _connectedAccountPhoto.value = profile.photoUrl
        prefs.edit()
            .putString("google_oauth_email_v2", profile.email)
            .putString("google_oauth_name_v2", profile.displayName)
            .putString("google_oauth_photo_v2", profile.photoUrl)
            .apply()
    }

    fun clearConnectedGoogleProfile() {
        _connectedAccount.value = ""
        _connectedAccountName.value = ""
        _connectedAccountPhoto.value = ""
        prefs.edit()
            .remove("google_oauth_email_v2")
            .remove("google_oauth_name_v2")
            .remove("google_oauth_photo_v2")
            .apply()
    }

    fun setAutoCloudSyncEnabled(enabled: Boolean) {
        _autoCloudSyncEnabled.value = enabled
        prefs.edit().putBoolean("auto_cloud_sync_enabled", enabled).apply()
    }

    fun setCustomWebClientId(clientId: String) {
        val clean = clientId.trim()
        _customWebClientId.value = clean
        prefs.edit().putString("custom_web_client_id", clean).apply()
    }

    fun recordCloudBackupSuccess(meta: DriveBackupFileMetadata) {
        _lastCloudBackupTime.value = meta.modifiedTimeFormatted
        _lastCloudBackupSize.value = meta.formattedSize
        prefs.edit()
            .putString("last_cloud_backup_time", meta.modifiedTimeFormatted)
            .putString("last_cloud_backup_size", meta.formattedSize)
            .apply()
    }

    fun saveSettings(
        managerPhone: String,
        currency: String,
        userName: String,
        salesStartCounter: Int? = null,
        supplyStartCounter: Int? = null
    ) {
        val newCurrency = currency.trim().ifBlank { "ر.ي" }
        val newUserName = userName.trim()
        val changedFormatting = (newCurrency != _currency.value) || (newUserName != _userName.value)

        _managerPhone.value = managerPhone.trim()
        _currency.value = newCurrency
        _userName.value = newUserName
        val editor = prefs.edit()
            .putString("manager_phone", _managerPhone.value)
            .putString("currency", _currency.value)
            .putString("user_name", _userName.value)
        if (salesStartCounter != null && salesStartCounter >= 1) {
            _salesCounter.value = salesStartCounter
            editor.putInt("sales_counter", salesStartCounter)
        }
        if (supplyStartCounter != null && supplyStartCounter >= 1) {
            _supplyCounter.value = supplyStartCounter
            editor.putInt("supply_counter", supplyStartCounter)
        }
        editor.apply()

        if (changedFormatting) {
            viewModelScope.launch(Dispatchers.IO) {
                TradeRepositoryOperations.upgradeLegacyMessagesToDirectFormat(dao, newCurrency, newUserName)
            }
        }
    }

    private fun scheduleSilentAutoBackup() {
        autoBackupJob?.cancel()
        autoBackupJob = viewModelScope.launch(Dispatchers.IO) {
            delay(1200L)
            performSilentAutoBackup()
        }
    }

    private suspend fun performSilentAutoBackup() {
        val json = buildFullBackupString()
        if (PdfAndBackupHelper.saveAutoLocalBackup(getApplication(), json) != null) {
            val dt = getCurrentDateTimeArabic()
            val timeStr = "${dt.dayName} ${dt.date} (${dt.time12h})"
            _lastAutoBackupTime.value = timeStr
            prefs.edit().putString("last_backup_time", timeStr).apply()
        }
        val activeEmail = _connectedAccount.value
        if (_autoCloudSyncEnabled.value && activeEmail.isNotBlank()) {
            try {
                val res = GoogleDriveCloudBackupManager.uploadBackupToDrive(getApplication(), activeEmail, json)
                if (res is DriveCloudResult.Success) {
                    recordCloudBackupSuccess(res.data)
                }
            } catch (_: Exception) {}
        }
    }

    suspend fun buildFullBackupString(): String = withContext(Dispatchers.IO) {
        PdfAndBackupHelper.buildFullBackupJson(
            products = dao.getAllProducts(),
            customers = dao.getRecentCustomersForBackup(2500),
            invoices = dao.getRecentSalesInvoicesForBackup(5000),
            supplies = dao.getRecentSuppliesForBackup(3000),
            salesCounter = _salesCounter.value,
            supplyCounter = _supplyCounter.value,
            managerPhone = _managerPhone.value,
            connectedAccount = _connectedAccount.value,
            userName = _userName.value
        )
    }

    fun restoreFromBackupJson(context: Context, jsonStr: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val res = db.withTransaction {
                    TradeRepositoryOperations.parseAndRestoreBackupJson(
                        dao, jsonStr, _salesCounter.value, _supplyCounter.value, _managerPhone.value, _userName.value
                    )
                }
                _salesCounter.value = res.salesCounter
                _supplyCounter.value = res.supplyCounter
                _managerPhone.value = res.managerPhone
                _userName.value = res.userName
                prefs.edit()
                    .putInt("sales_counter", res.salesCounter)
                    .putInt("supply_counter", res.supplyCounter)
                    .putString("manager_phone", res.managerPhone)
                    .putString("user_name", res.userName)
                    .apply()
                withContext(Dispatchers.Main) { Toast.makeText(context, "تمت استعادة النسخة الاحتياطية بنجاح ✓", Toast.LENGTH_LONG).show() }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { Toast.makeText(context, "ملف النسخة الاحتياطية غير صالح", Toast.LENGTH_SHORT).show() }
            }
        }
    }
}
