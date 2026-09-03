package com.vnazarov.resourcemonitor.presentation.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun StartScreen(
    modifier: Modifier = Modifier,
    paddingValues: PaddingValues = PaddingValues.Zero,
    onRegisterChange: () -> Unit = {}
) {
    Column(
        modifier = modifier
            .padding(paddingValues)
            .fillMaxSize(),
        verticalArrangement = Arrangement.Center
    ) {
        Button(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 64.dp),
            onClick = {
                onRegisterChange()
            }
        ) {
            Text(
                text = "Turn on/off",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Preview
@Composable
fun StartScreenPreview() {
    StartScreen()
}