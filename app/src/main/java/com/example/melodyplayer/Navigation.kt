package com.example.melodyplayer

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.example.melodyplayer.ui.PlayerScreen
import com.example.melodyplayer.ui.SongListScreen

@Composable
fun MainNavigation() {
  val backStack = rememberNavBackStack(SongList)
  val viewModel: PlaybackViewModel = viewModel()

  NavDisplay(
    backStack = backStack,
    onBack = { backStack.removeLastOrNull() },
    entryProvider =
      entryProvider {
        entry<SongList> {
          SongListScreen(
            viewModel = viewModel,
            onNavigateToPlayer = { backStack.add(Player) },
            modifier = Modifier.fillMaxSize()
          )
        }
        entry<Player> {
          PlayerScreen(
            viewModel = viewModel,
            onBack = { backStack.removeLastOrNull() },
            modifier = Modifier.fillMaxSize()
          )
        }
      },
  )
}
