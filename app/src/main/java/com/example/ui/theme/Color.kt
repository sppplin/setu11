package com.example.ui.theme

import androidx.compose.ui.graphics.Color

// Rozgaar Setu High-Contrast Warm Indian Theme Palette (Custom-designed for best readability & accessibility)
val OrangePrimary = Color(0xFFFF6F00)     // Rich, vibrant, modern high-visibility Amber-Orange
val OrangeClassic = Color(0xFFD84315)     // Deep rich terracotta brick orange for premium highlights
val SandyBg = Color(0xFFFCF9F5)           // Warm Cream Sand / Light Soft Beige Background
val ContrastLight = Color(0xFFF5EFEB)     // Cozy soft sandstone container level background
val CleanWhite = Color(0xFFFFFFFF)        // Card and input field backgrounds

val AcceptGreen = Color(0xFF1B5E20)       // Deep Forest Green for "स्वीकार करें" (Accept)
val UnderlineGreen = Color(0xFF4CAF50)    // Secondary energetic accent green
val DeclineRed = Color(0xFFC62828)        // Rich Deep Crimson Red for "मना करें" (Decline)

val DarkCharcoal = Color(0xFF1E1C21)      // Ultra-dark charcoal for pristine readability and high contrast
val MutedSlate = Color(0xFF5D5966)        // Sophisticated warm gray slate for secondary details
val GrayBorder = Color(0xFFE8E3DD)        // Soft earthy divider line/border color

// Legacy mapping variables to ensure absolute zero compilation/runtime problems
val TealPrimary = OrangePrimary
val TealDark = OrangeClassic
val TealLight = ContrastLight

val EmeraldAccent = AcceptGreen
val EmeraldLight = UnderlineGreen

val AmberHighlight = Color(0xFFFFB300)
val AmberLight = Color(0xFFFFF8E1)

val SlateBg = SandyBg
val DarkText = DarkCharcoal
val GrayText = MutedSlate
val BorderColor = GrayBorder

val CardBg = CleanWhite
val HeaderBg = OrangeClassic
val SearchBg = ContrastLight
val PrimaryCardBg = CleanWhite
