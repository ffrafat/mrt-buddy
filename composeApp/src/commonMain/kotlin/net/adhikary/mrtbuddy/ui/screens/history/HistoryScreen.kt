package net.adhikary.mrtbuddy.ui.screens.history

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import mrtbuddy.composeapp.generated.resources.Res
import mrtbuddy.composeapp.generated.resources.cancel
import mrtbuddy.composeapp.generated.resources.delete
import mrtbuddy.composeapp.generated.resources.deleteCard
import mrtbuddy.composeapp.generated.resources.deleteCardConfirm
import mrtbuddy.composeapp.generated.resources.noCardsFound
import mrtbuddy.composeapp.generated.resources.scanCardPrompt
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import kotlin.math.roundToInt

@Composable
fun HistoryScreen(
    modifier: Modifier = Modifier,
    onCardSelected: (String) -> Unit,
    viewModel: HistoryScreenViewModel = koinViewModel(),
) {
    val uiState = viewModel.state.collectAsState().value
    LaunchedEffect(Unit) {
        viewModel.onAction(HistoryScreenAction.OnInit)
    }
    
    var showRenameDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var cardToRename by remember { mutableStateOf<Pair<String, String?>>("" to null) }
    var cardToDelete by remember { mutableStateOf("") }
    
    // State to track active card index in the stack
    var activeCardIndex by remember { mutableStateOf(0) }

    // Reset index if cards list size changes drastically to avoid out of bounds
    LaunchedEffect(uiState.cards.size) {
        if (activeCardIndex >= uiState.cards.size && uiState.cards.isNotEmpty()) {
            activeCardIndex = 0
        }
    }

    if (showRenameDialog) {
        RenameDialog(
            currentName = cardToRename.second,
            onDismiss = { showRenameDialog = false },
            onConfirm = { newName ->
                cardToRename.first?.let { cardIdm ->
                    viewModel.onAction(HistoryScreenAction.RenameCard(cardIdm, newName))
                }
            }
        )
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(Res.string.deleteCard)) },
            text = { Text(stringResource(Res.string.deleteCardConfirm)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.onAction(HistoryScreenAction.DeleteCard(cardToDelete))
                        showDeleteDialog = false
                        // Reset index if needed
                        if (activeCardIndex >= uiState.cards.size - 1 && activeCardIndex > 0) {
                            activeCardIndex--
                        }
                    }
                ) {
                    Text(stringResource(Res.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(stringResource(Res.string.cancel))
                }
            }
        )
    }


    if (uiState.isLoading) {
        // Display a loading indicator
    } else if (uiState.error != null) {
        // Display the error message
        Text("Error: ${uiState.error}")
    } else if (uiState.cards.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize().then(modifier),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = stringResource(Res.string.noCardsFound),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Text(
                    text = stringResource(Res.string.scanCardPrompt),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    modifier = Modifier.padding(horizontal = 32.dp),
                    textAlign = TextAlign.Center
                )
            }
        }
    } else {
        val cards = uiState.cards
        
        Column(modifier = Modifier.fillMaxSize().then(modifier)) {
            // Top section: Overlapping Vertical Stack with Swipe
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(320.dp) // Increased height for the stack area
                    .padding(top = 24.dp),
                contentAlignment = Alignment.TopCenter
            ) {
                // Vertical Carousel Pointer / Indicator
                if (cards.size > 1) {
                    Column(
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .padding(end = 16.dp)
                            .height(200.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center
                    ) {
                         cards.indices.forEach { index ->
                             val isSelected = index == activeCardIndex
                             Box(
                                 modifier = Modifier
                                     .padding(vertical = 4.dp)
                                     .size(if (isSelected) 10.dp else 6.dp)
                                     .background(
                                         color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                                         shape = CircleShape
                                     )
                             )
                         }
                    }
                }

                // Stack Rendering Logic
                var dragOffset by remember { mutableStateOf(0f) }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = 60.dp),
                    contentAlignment = Alignment.TopCenter
                ) {
                     // We want to show the active card and N cards "behind" it in a loop.
                     // Total cards in stack visualization (including active)
                     val visibleStackCount = 3.coerceAtMost(cards.size)

                     // Render from back to front so active is on top
                     // The last item to render is the active one (offset 0)
                     // The first item to render is the furthest back (offset N)
                     
                     for (i in (visibleStackCount - 1) downTo 0) {
                         // Calculate index in the circular list
                         val cardIndex = (activeCardIndex + i) % cards.size
                         val cardWithBalance = cards[cardIndex]
                         
                         val isTopCard = i == 0
                         
                         // Offset: 
                         // i=0 (Active) -> Y=0
                         // i=1 (Behind) -> Y=-40
                         // i=2 (Behind) -> Y=-80
                         val targetOffsetY = (-i * 40).dp
                         val targetScale = 1f - (i * 0.05f)
                         
                         val animatedOffsetY by animateDpAsState(targetValue = targetOffsetY, animationSpec = tween(durationMillis = 300))
                         val animatedScale by animateFloatAsState(targetValue = targetScale, animationSpec = tween(durationMillis = 300))
                         
                         val currentDrag = if (isTopCard) dragOffset else 0f
                         
                         Box(
                             modifier = Modifier
                                 .width(340.dp)
                                 .offset { IntOffset(0, currentDrag.roundToInt()) } // Vertical Swipe
                                 .offset(y = animatedOffsetY)
                                 .scale(animatedScale)
                                 .zIndex(-i.toFloat()) // 0 is top, 1 is -1
                                 .then(
                                     if (isTopCard) {
                                         Modifier.draggable(
                                             orientation = Orientation.Vertical,
                                             state = rememberDraggableState { delta ->
                                                 dragOffset += delta
                                             },
                                             onDragStopped = {
                                                 val threshold = 100f
                                                 if (dragOffset > threshold) { 
                                                     // Swipe Down -> Previous Card (Circular)
                                                     activeCardIndex = if (activeCardIndex - 1 < 0) cards.size - 1 else activeCardIndex - 1
                                                 } else if (dragOffset < -threshold) { 
                                                     // Swipe Up -> Next Card (Circular)
                                                     activeCardIndex = (activeCardIndex + 1) % cards.size
                                                 }
                                                 dragOffset = 0f
                                             }
                                         )
                                     } else Modifier
                                 )
                         ) {
                             WalletCard(
                                 card = cardWithBalance.card,
                                 onClick = {}
                             )
                         }
                     }
                }
            }

            // Bottom section: Details of Selected Card
            if (cards.isNotEmpty()) {
                val selectedCard = cards[activeCardIndex]
                CardDetailsView(
                    card = selectedCard.card,
                    balance = selectedCard.balance,
                    onRenameClick = {
                        cardToRename = selectedCard.card.idm to selectedCard.card.name
                        showRenameDialog = true
                    },
                    onDeleteClick = {
                        cardToDelete = selectedCard.card.idm
                        showDeleteDialog = true
                    },
                    onViewTransactionsClick = { onCardSelected(selectedCard.card.idm) },
                    modifier = Modifier.weight(1f) // Take remaining space
                )
            }
        }
    }
}
