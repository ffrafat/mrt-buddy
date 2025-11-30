package net.adhikary.mrtbuddy.repository

import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import net.adhikary.mrtbuddy.dao.CardDao
import net.adhikary.mrtbuddy.dao.ScanDao
import net.adhikary.mrtbuddy.dao.TransactionDao
import net.adhikary.mrtbuddy.data.CardEntity
import net.adhikary.mrtbuddy.data.ScanEntity
import net.adhikary.mrtbuddy.data.TransactionEntity
import net.adhikary.mrtbuddy.data.TransactionEntityWithAmount
import net.adhikary.mrtbuddy.model.CardReadResult
import net.adhikary.mrtbuddy.nfc.service.TimestampService
import net.adhikary.mrtbuddy.utils.isRapidPassIdm

class TransactionRepository(
    private val cardDao: CardDao,
    private val scanDao: ScanDao,
    private val transactionDao: TransactionDao
) {

    suspend fun saveCardReadResult(result: CardReadResult) {
        val currentTime = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
        
        // Check if card exists to determine if we need to set a default name
        val existingCard = cardDao.getCardByIdm(result.idm)
        val cardName = if (existingCard == null || existingCard.name == null) {
            // Count existing cards of the same type to assign "MRT Pass 1", "MRT Pass 2", etc.
            val allCards = cardDao.getAllCards()
            val isRapid = isRapidPassIdm(result.idm)
            val typePrefix = if (isRapid) "Rapid Pass" else "MRT Pass"
            
            // Filter cards that are of the same type
            val sameTypeCount = allCards.count { 
                isRapidPassIdm(it.idm) == isRapid 
            }
            
            "$typePrefix ${sameTypeCount + 1}"
        } else {
            existingCard.name
        }

        val cardEntity = CardEntity(idm = result.idm, name = cardName, lastScanTime = currentTime)
        cardDao.insertCard(cardEntity) // This handles conflict with REPLACE strategy usually, or we need to be careful
        // If insertCard uses OnConflictStrategy.REPLACE, it overwrites name if we don't pass it.
        // The CardEntity data class has name. If we pass a new CardEntity with the name logic above, it preserves or sets it.
        
        cardDao.updateLastScanTime(result.idm, currentTime)

        val scanEntity = ScanEntity(cardIdm = result.idm)
        val scanId = scanDao.insertScan(scanEntity)

        val newTransactionEntities = result.transactions.map { txn ->
            val dateTime = txn.timestamp
                .toInstant(TimestampService.getDefaultTimezone())
                .toEpochMilliseconds()

            TransactionEntity(
                cardIdm = result.idm,
                scanId = scanId,
                fromStation = txn.fromStation,
                toStation = txn.toStation,
                balance = txn.balance,
                dateTime = dateTime,
                fixedHeader = txn.fixedHeader
            )
        }

        val lastOrder = transactionDao.getLastOrder() ?: 0
        val transactionsToInsert = newTransactionEntities
            .reversed()
            .mapIndexed { index, entity ->
                entity.copy(order = lastOrder + index + 1)
            }

        transactionDao.insertTransactions(transactionsToInsert)
    }

    suspend fun getCardByIdm(idm: String): CardEntity? {
        return cardDao.getCardByIdm(idm)
    }

    suspend fun getAllCards(): List<CardEntity> {
        return cardDao.getAllCards()
    }

    suspend fun getTransactionsByCardIdm(cardIdm: String): List<TransactionEntityWithAmount> {
        val transactions = transactionDao.getTransactionsByCardIdm(cardIdm)

        val sortedTransactions = transactions.sortedByDescending { it.order }
        return sortedTransactions.mapIndexed { index, transaction ->
            val amount = if (index + 1 < sortedTransactions.size) {
                transaction.balance - sortedTransactions[index + 1].balance
            } else {
                null
            }
            TransactionEntityWithAmount(transactionEntity = transaction, amount = amount)
        }.filter { transaction -> transaction.amount != null }
    }

    suspend fun getLatestBalanceByCardIdm(cardIdm: String): Int? {
        return transactionDao.getLatestTransactionByCardIdm(cardIdm)?.balance
    }

    suspend fun renameCard(cardIdm: String, newName: String) {
        cardDao.updateCardName(cardIdm, newName)
    }

    suspend fun deleteCard(cardIdm: String) {
        cardDao.deleteCard(cardIdm)
        scanDao.deleteScansByCardIdm(cardIdm)
        transactionDao.deleteTransactionsByCardIdm(cardIdm)
    }
}
