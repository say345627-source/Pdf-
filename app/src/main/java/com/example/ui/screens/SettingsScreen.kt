package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PictureInPicture
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.UserPreferencesRepository
import com.example.ui.theme.WaraqBorder
import com.example.ui.theme.WaraqDarkBg
import com.example.ui.theme.WaraqGreenContainer
import com.example.ui.theme.WaraqNeonGreen
import com.example.ui.theme.WaraqSurfaceElevated
import com.example.ui.theme.WaraqTextPrimary
import com.example.ui.theme.WaraqTextSecondary
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefsRepo = remember { UserPreferencesRepository(context) }
    val prefs by prefsRepo.preferencesFlow.collectAsState(initial = com.example.data.local.UserPreferences())

    BackHandler { onBack() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "الإعدادات",
                        style = MaterialTheme.typography.titleLarge,
                        color = WaraqTextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("settings_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "رجوع",
                            tint = WaraqTextPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = WaraqDarkBg)
            )
        },
        containerColor = WaraqDarkBg,
        modifier = modifier.testTag("settings_screen")
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Widget Instructions Card
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .border(1.dp, WaraqBorder, RoundedCornerShape(16.dp)),
                color = WaraqSurfaceElevated
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(WaraqGreenContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Widgets,
                            contentDescription = null,
                            tint = WaraqNeonGreen,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "ويدجت الشاشة الرئيسية (Home Widget)",
                            style = MaterialTheme.typography.titleMedium,
                            color = WaraqTextPrimary,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "لإضافة أداة «وَرَق» التفاعلية: اضغط مطولاً على أي مساحة فارغة في شاشة هاتفك الرئيسية، ثم اختر «التطبيقات المصغرة / Widgets»، وابحث عن «وَرَق» وأضفها للوصول الفوري لكتبك بلمسة واحدة.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = WaraqTextSecondary,
                            lineHeight = 22.sp
                        )
                    }
                }
            }

            // Word Reading Preferences
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .border(1.dp, WaraqBorder, RoundedCornerShape(16.dp)),
                color = WaraqSurfaceElevated
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.FormatSize, contentDescription = null, tint = WaraqNeonGreen)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "حجم الخط الافتراضي (مستندات Word)",
                            style = MaterialTheme.typography.titleMedium,
                            color = WaraqTextPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "${prefs.wordFontSizeSp.toInt()} نقطة",
                            color = WaraqNeonGreen,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            modifier = Modifier.width(60.dp)
                        )

                        Slider(
                            value = prefs.wordFontSizeSp,
                            onValueChange = {
                                scope.launch { prefsRepo.setWordFontSize(it) }
                            },
                            valueRange = 13f..28f,
                            colors = SliderDefaults.colors(
                                thumbColor = WaraqNeonGreen,
                                activeTrackColor = WaraqNeonGreen,
                                inactiveTrackColor = WaraqDarkBg
                            ),
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Text(
                        text = "نموذج: النص العربي الواضح والمريح للعين أثناء القراءة الطويلة.",
                        fontSize = prefs.wordFontSizeSp.sp,
                        color = WaraqTextSecondary,
                        lineHeight = (prefs.wordFontSizeSp * 1.5f).sp,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }

            // PDF Display Preferences
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .border(1.dp, WaraqBorder, RoundedCornerShape(16.dp)),
                color = WaraqSurfaceElevated
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.PictureInPicture, contentDescription = null, tint = WaraqNeonGreen)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "نمط عرض صفحات PDF",
                            style = MaterialTheme.typography.titleMedium,
                            color = WaraqTextPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        RadioButton(
                            selected = prefs.pdfFitMode == "WIDTH",
                            onClick = {
                                scope.launch { prefsRepo.setPdfFitMode("WIDTH") }
                            },
                            colors = RadioButtonDefaults.colors(selectedColor = WaraqNeonGreen)
                        )
                        Text(
                            text = "ملاءمة العرض (موصى به للنصوص)",
                            color = WaraqTextPrimary,
                            fontSize = 14.sp
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        RadioButton(
                            selected = prefs.pdfFitMode == "PAGE",
                            onClick = {
                                scope.launch { prefsRepo.setPdfFitMode("PAGE") }
                            },
                            colors = RadioButtonDefaults.colors(selectedColor = WaraqNeonGreen)
                        )
                        Text(
                            text = "ملاءمة كامل الصفحة (للمخططات والرسوم)",
                            color = WaraqTextPrimary,
                            fontSize = 14.sp
                        )
                    }
                }
            }

            // Privacy and Local Storage Info
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .border(1.dp, WaraqBorder, RoundedCornerShape(16.dp)),
                color = WaraqSurfaceElevated
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Security, contentDescription = null, tint = WaraqNeonGreen)
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "خصوصية تامة ومعالجة محلية 100%",
                            style = MaterialTheme.typography.titleSmall,
                            color = WaraqTextPrimary,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "جميع المستندات تُقرأ وتُحفظ محلياً على جهازك دون رفع أي بيانات لأي خوادم خارجية.",
                            style = MaterialTheme.typography.bodySmall,
                            color = WaraqTextSecondary
                        )
                    }
                }
            }

            // About App Card
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .border(1.dp, WaraqBorder, RoundedCornerShape(16.dp)),
                color = WaraqSurfaceElevated
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoStories,
                        contentDescription = null,
                        tint = WaraqNeonGreen,
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "وَرَق • WARAQ Reader",
                        style = MaterialTheme.typography.titleMedium,
                        color = WaraqTextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "الإصدار 1.0 (Native Android)",
                        style = MaterialTheme.typography.labelSmall,
                        color = WaraqTextSecondary
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "تطبيق متكامل لقراءة ملفات PDF وWord مع دعم الويدجت التفاعلي لمتابعة القراءة وحفظ الموضع تلقائياً.",
                        style = MaterialTheme.typography.bodySmall,
                        color = WaraqTextSecondary,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}
