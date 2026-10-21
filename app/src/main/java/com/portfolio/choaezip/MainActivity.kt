package com.portfolio.choaezip

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.NoCredentialException
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.launch
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID

val Purple = Color(0xFF8955CE)
val Ink = Color(0xFF2E2539)
val Muted = Color(0xFF91809F)
val Lavender = Color(0xFFF1EBF8)
private val Seoul = ZoneId.of("Asia/Seoul")
fun dateTime(ms: Long) = Instant.ofEpochMilli(ms).atZone(Seoul).format(DateTimeFormatter.ofPattern("M월 d일 (E) · a h:mm", java.util.Locale.KOREAN))
class MainActivity : ComponentActivity() {
    private var incoming by mutableStateOf<Intent?>(null)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState); enableEdgeToEdge(); incoming = intent
        setContent {
            MaterialTheme(colorScheme = lightColorScheme(primary=Purple, secondary=Muted, background=Color(0xFFFDFBFF), surface=Color(0xFFFDFBFF), onSurface=Ink, onBackground=Ink, primaryContainer=Lavender)) {
                val vm: AppViewModel = viewModel()
                LaunchedEffect(incoming) {
                    incoming?.let { i ->
                        val uri = i.data
                        if(uri?.getQueryParameter("mode")=="resetPassword") vm.navigate("newPassword",uri.getQueryParameter("oobCode") ?: "")
                        else if(i.hasExtra("notificationId")) {
                            if(Backend.auth.currentUser?.uid == i.getStringExtra("recipientRef")) vm.navigate("notifications")
                            else vm.message = "해당 계정으로 로그인한 뒤 알림함을 확인해 주세요."
                        }
                    }
                    incoming = null
                }
                ChoaezipApp(vm)
            }
        }
    }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); incoming = intent }
}
private val titles = mapOf("home" to "최애.zip", "calendar" to "일정", "records" to "덕질 보관함", "lounge" to "팬라운지", "my" to "마이", "artists" to "최애 선택", "schedule" to "일정 상세", "record" to "기록 상세", "editRecord" to "기록 남기기", "post" to "이야기", "editPost" to "이야기 쓰기", "login" to "로그인", "signup" to "이메일 회원가입", "completeSignup" to "가입 완료", "reset" to "비밀번호 찾기", "newPassword" to "비밀번호 변경", "notifications" to "알림함", "pushTest" to "알림 수신 확인", "preferences" to "알림 설정", "profile" to "프로필 수정", "blocks" to "차단 사용자", "report" to "신고와 차단", "deleteAccount" to "계정 탈퇴")
@OptIn(ExperimentalMaterial3Api::class)
@Composable fun ChoaezipApp(vm: AppViewModel) {
    val root = vm.route.screen in listOf("home","calendar","records","lounge","my")
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(vm.message) { vm.message?.let { snackbar.showSnackbar(it); vm.message = null } }
    BackHandler(enabled=vm.route.screen!="home") { if(vm.route.screen=="completeSignup") vm.cancelSocial() else vm.back() }
    Scaffold(
        topBar = { TopAppBar(title={ Text(titles[vm.route.screen] ?: "최애.zip", fontWeight=FontWeight.Bold) }, navigationIcon={ if(!root) IconButton(onClick={ if(vm.route.screen=="completeSignup") vm.cancelSocial() else vm.back() }) { Icon(Icons.Outlined.ArrowBack,"뒤로") } }, actions={ if(root) IconButton(onClick={ vm.navigate("pushTest") }) { Icon(Icons.Outlined.Notifications,"알림함") } }) },
        bottomBar = { if(root) NavigationBar(containerColor=Color.White) {
            listOf(Triple("home","홈",Icons.Outlined.Home),Triple("calendar","일정",Icons.Outlined.CalendarMonth),Triple("records","기록",Icons.Outlined.FavoriteBorder),Triple("lounge","라운지",Icons.Outlined.Forum),Triple("my","마이",Icons.Outlined.PersonOutline)).forEach { (route,label,icon) -> NavigationBarItem(selected=vm.route.screen==route,onClick={vm.authorMe=false; vm.navigate(route)},icon={Icon(icon,label)},label={Text(label)}) }
        } }, snackbarHost={ SnackbarHost(snackbar) }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).imePadding()) {
            if(vm.busy || vm.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            vm.error?.let { msg -> Surface(color=MaterialTheme.colorScheme.errorContainer) { Row(Modifier.padding(12.dp),verticalAlignment=Alignment.CenterVertically) { Text(msg,Modifier.weight(1f),fontSize=13.sp); TextButton(onClick={vm.refresh()}) {Text("다시 조회")} } } }
            key(vm.route) {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal=20.dp).padding(bottom=32.dp),verticalArrangement=Arrangement.spacedBy(18.dp)) {
                    Spacer(Modifier.height(2.dp))
                    when(vm.route.screen) {
                        "home" -> Home(vm)
                        "artists" -> ArtistPicker(vm)
                        "calendar" -> CalendarScreen(vm)
                        "schedule" -> ScheduleDetail(vm)
                        "records" -> Records(vm)
                        "record" -> ContentDetail(vm,true)
                        "editRecord" -> Editor(vm,true)
                        "lounge" -> Lounge(vm)
                        "post" -> ContentDetail(vm,false)
                        "editPost" -> Editor(vm,false)
                        "my" -> MyScreen(vm)
                        "login", "signup", "completeSignup", "reset", "newPassword" -> AuthScreen(vm)
                        "notifications" -> Notifications(vm)
                        "pushTest" -> PushTestScreen(vm)
                        "preferences" -> Preferences(vm)
                        "profile" -> Profile(vm)
                        "blocks" -> Blocks(vm)
                        "report" -> Report(vm)
                        "deleteAccount" -> DeleteAccount(vm)
                    }
                }
            }
        }
    }
    if(vm.busy) Dialog(onDismissRequest={}) {
        Surface(shape=RoundedCornerShape(20.dp),color=MaterialTheme.colorScheme.surface) {
            Row(Modifier.padding(24.dp),horizontalArrangement=Arrangement.spacedBy(20.dp),verticalAlignment=Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(28.dp),strokeWidth=3.dp)
                Text("처리 중이에요…")
            }
        }
    }
    if(vm.loginPrompt) ModalBottomSheet(onDismissRequest={vm.loginPrompt=false}) {
        Column(Modifier.padding(24.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
            Heading("이 순간을 함께 저장해요")
            Text("로그인하면 기록, 알림, 팬라운지 참여 기능을 이용할 수 있어요.",color=Muted)
            Primary("로그인하기") { vm.loginPrompt=false; vm.navigate("login") }
            TextButton(onClick={vm.loginPrompt=false},modifier=Modifier.fillMaxWidth()) {Text("계속 둘러보기")}
        }
    }
    if(vm.discardPrompt) AlertDialog(onDismissRequest={vm.discardPrompt=false},title={Text("작성을 잠시 멈출까요?")},text={Text(if(vm.route.screen=="profile") "저장하지 않은 프로필 변경은 사라져요." else "입력한 내용은 이 계정의 기기에 임시 보관됩니다. 사진은 다시 선택해 주세요.")},confirmButton={TextButton(onClick={vm.backNow()}){Text(if(vm.route.screen=="profile") "저장하지 않고 나가기" else "임시 저장 후 나가기")}},dismissButton={TextButton(onClick={vm.discardPrompt=false}){Text("계속 작성")}})
}
@Composable fun Heading(text: String) { Text(text,fontSize=22.sp,fontWeight=FontWeight.Bold,lineHeight=30.sp) }
@Composable fun Caption(text: String) { Text(text,color=Muted,fontSize=13.sp) }
@Composable fun Primary(text: String, enabled: Boolean=true, onClick: ()->Unit) { Button(onClick,Modifier.fillMaxWidth().heightIn(min=52.dp),enabled=enabled,shape=RoundedCornerShape(16.dp)) { Text(text,fontWeight=FontWeight.SemiBold) } }
@Composable fun Panel(modifier: Modifier=Modifier, color: Color=Lavender, onClick: (() -> Unit)?=null, content: @Composable ColumnScope.()->Unit) {
    Surface(modifier.fillMaxWidth().then(if(onClick!=null) Modifier.clickable(onClick=onClick) else Modifier),shape=RoundedCornerShape(22.dp),color=color) { Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp),content=content) }
}
@Composable fun EmptyState(title: String, subtitle: String) { Panel { Text(title,fontWeight=FontWeight.Bold); Caption(subtitle) } }
@Composable fun MenuRow(text: String, detail: String="", action: ()->Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick=action).padding(vertical=12.dp),verticalAlignment=Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(text,fontSize=16.sp); if(detail.isNotEmpty()) Caption(detail) }; Icon(Icons.Outlined.ChevronRight,null,tint=Muted) }
    HorizontalDivider(color=Lavender)
}
@Composable fun Field(value: String, label: String, onChange:(String)->Unit, multiline:Boolean=false, password:Boolean=false, error:String?=null) {
    OutlinedTextField(value,onChange,isError=error!=null,supportingText=error?.let { message -> { Text(message) } },modifier=Modifier.fillMaxWidth().then(if(multiline) Modifier.heightIn(min=160.dp) else Modifier),label={Text(label)},shape=RoundedCornerShape(14.dp),singleLine=!multiline,visualTransformation=if(password) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,keyboardOptions=KeyboardOptions(keyboardType=if(password) KeyboardType.Password else KeyboardType.Text))
}
@Composable fun Chips(options: List<String>, value: String, select:(String)->Unit) {
    options.chunked(3).forEach { row -> Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) { row.forEach { label -> FilterChip(selected=value==label,onClick={select(label)},label={Text(label)}) } } }
}
@Composable fun Home(vm: AppViewModel) {
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Caption("나의 최애"); Heading(vm.favoriteName) }; IconButton(onClick={vm.navigate("artists")}) {Icon(Icons.Outlined.ExpandMore,"최애 선택")} }
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Brush.linearGradient(listOf(Purple,Color(0xFFAD83DF)))).padding(24.dp)) {
        Column(verticalArrangement=Arrangement.spacedBy(22.dp)) { Text("YOUR FANDOM, YOUR ZIP",color=Color.White.copy(alpha=.8f),fontSize=11.sp,letterSpacing=2.sp); Text("좋아하는 마음을\n차곡차곡 모아봐요",color=Color.White,fontSize=28.sp,fontWeight=FontWeight.Bold,lineHeight=39.sp); TextButton(onClick={vm.navigate("calendar")},colors=ButtonDefaults.textButtonColors(contentColor=Color.White),contentPadding=PaddingValues(0.dp)){Text("최애 일정 보러 가기  →")} }
    }
    if(vm.data.bool("cached")) Caption("저장된 일정 · 마지막 갱신 ${dateTime(vm.data.num("updatedAt"))}")
    Heading("놓치면 아쉬운 일정")
    val upcoming=vm.schedules.filter { it.num("startAt")>System.currentTimeMillis() }.take(3)
    if(upcoming.isEmpty()&&!vm.loading&&vm.error==null) EmptyState("다가오는 일정이 없어요","다른 달이나 최애를 선택해 보세요.")
    upcoming.forEach { ScheduleCard(it) {vm.navigate("schedule",it.str("id"))} }
    Heading("최애와 함께한 순간")
    Panel(onClick={vm.navigate("records")}) { Text("♡  덕질 보관함",fontSize=20.sp,fontWeight=FontWeight.Bold); Caption("나만 간직하는 소중한 추억") }
    Panel(color=Color(0xFFF9EAF2),onClick={vm.navigate("lounge")}) { Text("☆  팬라운지",fontSize=20.sp,fontWeight=FontWeight.Bold); Caption("같은 마음을 가진 팬들과 이야기해요") }
}
@Composable fun ArtistPicker(vm: AppViewModel) {
    var query by rememberSaveable { mutableStateOf("") }; var selected by rememberSaveable {mutableStateOf(vm.artistId)}
    Field(query,"그룹 또는 멤버 검색",{query=it;vm.searchArtists(it)})
    Caption("하나의 최애를 선택해 주세요. 그룹과 멤버는 별도로 선택할 수 있어요.")
    vm.artistResults.forEach { a -> Panel(color=if(selected==a.str("id")) Color(0xFFE7DAF6) else Lavender,onClick={selected=a.str("id")}) { Row(verticalAlignment=Alignment.CenterVertically) { Column(Modifier.weight(1f)) {Text(a.str("name"),fontWeight=FontWeight.Bold);Caption(if(a.str("type")=="group") "그룹" else "멤버")};RadioButton(selected==a.str("id"),onClick={selected=a.str("id")}) } } }
    if(vm.artistResults.isEmpty()&&!vm.loading&&vm.error==null) EmptyState("검색 결과가 없어요","검색어를 바꿔 보세요.")
    Primary("선택 완료",selected.isNotBlank()&&!vm.busy){vm.selectArtist(selected)}
}
@Composable fun ScheduleCard(s: Doc, action:()->Unit) {
    Panel(onClick=action) { Row(horizontalArrangement=Arrangement.spacedBy(14.dp),verticalAlignment=Alignment.CenterVertically) {
        val date=Instant.ofEpochMilli(s.num("startAt")).atZone(Seoul)
        Surface(shape=RoundedCornerShape(14.dp),color=Color(0xFFE4D7F4)) { Column(Modifier.padding(14.dp),horizontalAlignment=Alignment.CenterHorizontally){Text("${date.monthValue}월",color=Purple,fontSize=12.sp);Text("${date.dayOfMonth}",color=Purple,fontSize=27.sp)} }
        Column(verticalArrangement=Arrangement.spacedBy(6.dp)) {Text(s.str("title"),fontWeight=FontWeight.Bold);Caption(dateTime(s.num("startAt")));Text("${s.str("type")} · ${s.str("status")}",color=Purple,fontSize=12.sp)}
    } }
}
@Composable fun CalendarScreen(vm: AppViewModel) {
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween) {
        IconButton(onClick={vm.month=vm.month.minusMonths(1);vm.selectedDate=vm.month.atDay(1);vm.refresh()}){Icon(Icons.Outlined.ChevronLeft,"이전 달")}
        Heading("${vm.month.year}년 ${vm.month.monthValue}월")
        IconButton(onClick={vm.month=vm.month.plusMonths(1);vm.selectedDate=vm.month.atDay(1);vm.refresh()}){Icon(Icons.Outlined.ChevronRight,"다음 달")}
    }
    Row { listOf("월","화","수","목","금","토","일").forEach { Box(Modifier.weight(1f),contentAlignment=Alignment.Center){Caption(it)} } }
    val cells=List(vm.month.atDay(1).dayOfWeek.value-1){0}+(1..vm.month.lengthOfMonth()).toList()
    cells.chunked(7).forEach { week -> Row(Modifier.fillMaxWidth()) {
        (week + List(7-week.size){0}).forEach { day ->
            Box(Modifier.weight(1f).height(48.dp).clip(RoundedCornerShape(12.dp)).background(if(day>0&&vm.selectedDate==vm.month.atDay(day)) Purple else Color.Transparent).clickable(enabled=day>0){vm.selectedDate=vm.month.atDay(day)},contentAlignment=Alignment.Center) {
                if(day>0) Column(horizontalAlignment=Alignment.CenterHorizontally) { Text("$day",color=if(vm.selectedDate==vm.month.atDay(day)) Color.White else Ink); if(vm.schedules.any { Instant.ofEpochMilli(it.num("startAt")).atZone(Seoul).toLocalDate()==vm.month.atDay(day) }) Text("•",color=if(vm.selectedDate==vm.month.atDay(day)) Color.White else Purple,lineHeight=7.sp,fontSize=9.sp) }
            }
        }
    } }
    Chips(listOf("전체","방송","컴백","공연","티켓"),vm.filter.ifBlank{"전체"}){vm.filter=if(it=="전체")"" else it}
    Heading("${vm.selectedDate.monthValue}월 ${vm.selectedDate.dayOfMonth}일")
    val rows=vm.schedules.filter { Instant.ofEpochMilli(it.num("startAt")).atZone(Seoul).toLocalDate()==vm.selectedDate&&(vm.filter.isBlank()||it.str("type")==vm.filter) }
    rows.forEach { ScheduleCard(it){vm.navigate("schedule",it.str("id"))} }
    if(rows.isEmpty()&&!vm.loading&&vm.error==null) EmptyState("이날은 등록된 일정이 없어요","다른 날짜도 둘러보세요.")
    if(vm.data.num("updatedAt")>0) Caption("마지막 조회 · ${dateTime(vm.data.num("updatedAt"))}")
}
@Composable fun ScheduleDetail(vm: AppViewModel) {
    val s=vm.data; val context=LocalContext.current
    val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){vm.syncDevice()}
    if(s.isEmpty()) return
    Text("${s.str("type")} · ${s.str("status")}",color=Purple)
    Heading(s.str("title")); Panel {Text(dateTime(s.num("startAt")),fontWeight=FontWeight.Bold);Caption(s.str("location"))}
    Text(s.str("body"),lineHeight=26.sp)
    val url=s.str("sourceUrl")
    if(url.startsWith("https://")) MenuRow("공식 출처 확인",s.str("sourceName")){context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(url)))}
    if(s.num("updatedAt")>0) Caption("수정 · ${dateTime(s.num("updatedAt"))}")
    // Spark: server automatic reminder UI disabled.
/*
    val eligible=s.str("status")=="예정"&&s.num("startAt")>System.currentTimeMillis()+1800000
    Caption("일정 시작 30분 전에 한 번 알려드려요.")
    Primary(if(s.bool("reminded")) "알림 신청 해제" else if(eligible) "시작 알림 신청" else "지금은 알림 신청이 어려워요",!vm.busy&&(eligible||s.bool("reminded"))) {
        vm.requireLogin { if(!s.bool("reminded")&&Build.VERSION.SDK_INT>=33) permission.launch(Manifest.permission.POST_NOTIFICATIONS); vm.reminder(!s.bool("reminded")) }
    }
*/
}
@Composable fun Guest(vm: AppViewModel, title: String) { EmptyState(title,"로그인하면 나만의 최애 공간이 열려요.");Primary("로그인하기"){vm.navigate("login")} }
@Composable fun Records(vm: AppViewModel) {
    if(!vm.signedIn){Guest(vm,"소중한 순간을 남겨보세요");return}
    Caption("오직 나만 볼 수 있는 비공개 기록")
    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        FilterChip(selected=vm.filter.isBlank(),onClick={vm.filter="";vm.refresh()},label={Text("모든 최애")})
        if(vm.artistId.isNotBlank()) FilterChip(selected=vm.filter==vm.artistId,onClick={vm.filter=vm.artistId;vm.refresh()},label={Text("선택한 최애")})
    }
    Primary("＋ 새 기록 남기기"){vm.navigate("editRecord")}
    if(vm.items.isEmpty()&&!vm.loading&&vm.error==null) EmptyState("아직 기록이 없어요","첫 번째 덕질의 순간을 저장해 보세요.")
    vm.items.forEach { ContentCard(it,true){vm.navigate("record",it.str("id"))} }
    More(vm)
}
@Composable fun ContentCard(p: Doc, record: Boolean, click:()->Unit) { Panel(onClick=click) {Text(p.str("category"),color=Purple,fontSize=12.sp);Text(p.str("title"),fontWeight=FontWeight.Bold,fontSize=18.sp);Text(p.str("body"),maxLines=2,overflow=TextOverflow.Ellipsis,color=Muted,fontSize=14.sp);Caption(if(record) "${p.str("date")} · 나만 보기" else "${p.obj("author").str("nickname")} · ♡ ${p.num("likeCount")}")} }
@Composable fun More(vm: AppViewModel) { if(vm.cursor!=null) OutlinedButton(onClick={vm.refresh(true)},enabled=!vm.loading,modifier=Modifier.fillMaxWidth()){Text("더 보기")} }
@Composable fun Lounge(vm: AppViewModel) {
    Heading(if(vm.authorMe) "내 이야기" else vm.favoriteName)
    Chips(listOf("전체","자유 이야기","공연 후기"),vm.filter.ifBlank{"전체"}){vm.filter=if(it=="전체")"" else it;vm.refresh()}
    Primary("＋ 이야기 쓰기"){vm.requireLogin{vm.navigate("editPost")}}
    if(vm.items.isEmpty()&&!vm.loading&&vm.error==null) EmptyState("아직 이야기가 없어요","첫 이야기를 들려주세요.")
    vm.items.forEach { ContentCard(it,false){vm.navigate("post",it.str("id"))} }; More(vm)
}
@Composable fun ContentDetail(vm: AppViewModel, record: Boolean) {
    val d=vm.data; var deleting by remember {mutableStateOf(false)}
    var comment by remember {mutableStateOf("")};var commentKey by remember{mutableStateOf(UUID.randomUUID().toString())}
    if(d.isEmpty())return
    Caption(d.str("category")+if(record) " · ${d.str("date")} · 나만 보기" else " · ${d.obj("author").str("nickname")}")
    Heading(d.str("title"))
    d.strings("urls").forEach { url -> AsyncImage(url,"첨부 사진",Modifier.fillMaxWidth().heightIn(min=180.dp,max=400.dp).clip(RoundedCornerShape(20.dp))) }
    Text(d.str("body"),fontSize=16.sp,lineHeight=28.sp)
    val own=d.str("ownerId")==vm.me?.str("id")
    if(own) { MenuRow("수정하기"){vm.navigate(if(record)"editRecord" else "editPost",vm.route.id)};MenuRow("삭제하기"){deleting=true} }
    if(!record){
        Row(verticalAlignment=Alignment.CenterVertically) { OutlinedButton(onClick={vm.requireLogin{vm.like()}},enabled=!vm.busy){Icon(if(d.bool("liked"))Icons.Outlined.Favorite else Icons.Outlined.FavoriteBorder,null);Text("  ${d.num("likeCount")}")};Spacer(Modifier.weight(1f));if(!own) TextButton(onClick={vm.requireLogin{vm.reportTarget=mapOf("type" to "post","id" to vm.route.id,"postId" to vm.route.id,"userId" to d.str("ownerId"));vm.navigate("report")}}){Text("신고 · 차단")} }
        Heading("댓글")
        val comments=d.obj("comments").rows()
        comments.forEach { c -> Panel {Text(c.obj("author").str("nickname"),fontWeight=FontWeight.Bold);Text(c.str("body"));if(c.str("ownerId")==vm.me?.str("id")) TextButton(onClick={vm.deleteComment(c.str("id"))},enabled=!vm.busy){Text("삭제")} else TextButton(onClick={vm.requireLogin {vm.reportTarget=mapOf("type" to "comment","id" to c.str("id"),"postId" to vm.route.id,"userId" to c.str("ownerId"));vm.navigate("report")}}){Text("신고 · 차단")} } }
        if(comments.isEmpty()) Caption("첫 댓글을 남겨보세요.")
        if(d.obj("comments")["cursor"]!=null) TextButton(onClick={vm.moreComments()},enabled=!vm.busy){Text("이전 댓글 더 보기")}
        Field(comment,"댓글 (1~500자)",{if(it.length<=500)comment=it})
        Primary("댓글 등록",!vm.busy&&comment.isNotBlank()){vm.requireLogin{vm.comment(comment,commentKey){comment="";commentKey=UUID.randomUUID().toString()}}}
    }
    if(deleting) AlertDialog(onDismissRequest={deleting=false},title={Text("정말 삭제할까요?")},text={Text("삭제한 내용은 복구할 수 없어요.")},confirmButton={TextButton(onClick={deleting=false;vm.deleteContent(record)}){Text("삭제")}},dismissButton={TextButton(onClick={deleting=false}){Text("취소")}})
}
@Composable fun Editor(vm: AppViewModel, record: Boolean) {
    if(vm.route.id.isNotBlank()&&vm.loading) return
    if(vm.route.id.isNotBlank()&&vm.data.isEmpty()) return
    val draft=remember{vm.draft()}; val initial=vm.data
    var title by rememberSaveable {mutableStateOf(draft.str("title",initial.str("title")))}
    var body by rememberSaveable {mutableStateOf(draft.str("body",initial.str("body")))}
    var date by rememberSaveable {mutableStateOf(draft.str("date",initial.str("date",LocalDate.now(Seoul).toString())))}
    var category by rememberSaveable {mutableStateOf(draft.str("category",initial.str("category",if(record)"일상" else "자유 이야기")))}
    var artist by rememberSaveable {mutableStateOf(draft.str("artistId",initial.str("artistId",vm.artistId.ifBlank { "general" })))}
    // Spark: var photos / existing media disabled (no Storage usage).
    // var photos by remember { mutableStateOf<List<Uri>>(emptyList()) }
    val requestKey=rememberSaveable {draft.str("requestKey").ifBlank {UUID.randomUUID().toString()}}
    fun saveDraft(){vm.saveDraft(mapOf("title" to title,"body" to body,"date" to date,"category" to category,"artistId" to artist,"requestKey" to requestKey))}
    // Spark: photo picker disabled. Restore only together with a reviewed storage backend.
    Caption(if(record) "나만 보는 기록이에요. 마음껏 추억을 남겨주세요." else "누구나 볼 수 있는 글이에요. 직접 만들거나 허락받은 콘텐츠만 올려주세요.")
    Field(title,"제목 (2~${if(record)50 else 80}자)",{title=it;saveDraft()})
    Caption("아티스트")
    Row(Modifier.fillMaxWidth().clickable{artist="general";saveDraft()},verticalAlignment=Alignment.CenterVertically){RadioButton(artist=="general",onClick={artist="general";saveDraft()});Text("아티스트 미지정")}
    vm.artists.forEach { a -> Row(Modifier.fillMaxWidth().clickable{artist=a.str("id");saveDraft()},verticalAlignment=Alignment.CenterVertically){RadioButton(artist==a.str("id"),onClick={artist=a.str("id");saveDraft()});Text(a.str("name"))} }
    if(record) Field(date,"기록 날짜 · YYYY-MM-DD",{date=it;saveDraft()})
    Chips(if(record)listOf("공연","방송","일상","굿즈") else listOf("자유 이야기","공연 후기"),category){category=it;saveDraft()}
    Field(body,"${if(record)"오늘의 순간" else "이야기"} (최대 ${if(record)2000 else 3000}자)",{body=it;saveDraft()},multiline=true)
    // Spark: photo upload controls disabled.
/*
    Text("사진 ${existing.size+photos.size}/4",fontWeight=FontWeight.Bold)
    existing.forEachIndexed { index,path -> MenuRow("기존 사진 ${index+1} 제거"){existing=existing-path;saveDraft()} }
    photos.forEach { uri -> Row(verticalAlignment=Alignment.CenterVertically){AsyncImage(uri,"선택 사진",Modifier.size(70.dp).clip(RoundedCornerShape(12.dp)));TextButton(onClick={photos=photos-uri;saveDraft()}){Text("제거")}} }
    OutlinedButton(onClick={picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))},enabled=photos.size+existing.size<4){Icon(Icons.Outlined.AddPhotoAlternate,null);Text(" 사진 선택")}
    Caption("JPEG · PNG · WebP / 한 장당 최대 10MB. 사진은 임시 보관되지 않아요.")
*/
    Primary("저장하기",!vm.busy&&artist.isNotBlank()){vm.saveContent(record,title,body,date,category,artist,emptyList(),emptyList(),requestKey,initial.num("version"))}
}
@Composable fun MyScreen(vm: AppViewModel) {
    MenuRow("알림 수신 확인"){vm.navigate("pushTest")}
    if(!vm.signedIn){Guest(vm,"좋아하는 마음의 보관함");MenuRow("최애 선택"){vm.navigate("artists")};return}
    Panel {Text("✦",fontSize=32.sp,color=Purple);Heading(vm.nickname);Caption(vm.me?.str("bio")?:"");Text(vm.favoriteName,color=Purple)}
    if(vm.me?.bool("restricted")==true) Panel(color=MaterialTheme.colorScheme.errorContainer){Text("일부 기능 이용이 제한되어 있어요.");Caption(vm.me?.str("restrictionReason")?:"문의는 운영자에게 연락해 주세요.")}
    MenuRow("프로필 수정"){vm.navigate("profile")};MenuRow("최애 변경"){vm.navigate("artists")};MenuRow("내 기록"){vm.navigate("records")};MenuRow("내 게시글"){vm.authorMe=true;vm.navigate("lounge")} // Spark: automatic notification inbox disabled.
    MenuRow("알림 설정"){vm.navigate("preferences")};MenuRow("차단 사용자"){vm.navigate("blocks")};MenuRow("로그아웃"){vm.logout()};MenuRow("계정 탈퇴"){vm.navigate("deleteAccount")}
}
private suspend fun googleCredential(context: android.content.Context): GoogleIdTokenCredential = FirebaseTrace.span("auth.google.credential") {
    require(!BuildConfig.USE_EMULATORS){"Google 로그인은 실제 Firebase 설정과 SHA 인증서 등록 후 사용할 수 있어요. Emulator에서는 이메일로 가입해 주세요."}
    val res=context.resources.getIdentifier("default_web_client_id","string",context.packageName)
    require(res!=0){"Firebase Google 공급자와 google-services.json을 설정해 주세요."}
    val option=GetSignInWithGoogleOption.Builder(context.getString(res)).build()
    val result=try { CredentialManager.create(context).getCredential(context,GetCredentialRequest.Builder().addCredentialOption(option).build()) }
    catch (_: androidx.credentials.exceptions.GetCredentialCancellationException) { throw kotlinx.coroutines.CancellationException("Google sign-in cancelled") }
    catch (_: NoCredentialException) { error("사용 가능한 Google 계정이 없어요. 기기 계정을 확인해 주세요.") }
    require(result.credential.type==GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL){"Google 계정을 선택해 주세요."}
    GoogleIdTokenCredential.createFrom(result.credential.data)
}
@Composable fun AuthScreen(vm: AppViewModel) {
    val screen=vm.route.screen;val context=LocalContext.current
    var email by rememberSaveable{mutableStateOf("")}; var nickname by rememberSaveable{mutableStateOf(vm.pendingNickname)}
    var password by remember{mutableStateOf("")};var confirm by remember{mutableStateOf("")};var agree by rememberSaveable{mutableStateOf(vm.pendingAgreement)}
    val scope = rememberCoroutineScope()
    var selectingGoogle by remember { mutableStateOf(false) }
    var submitted by remember { mutableStateOf(false) }
    var googleNotice by remember { mutableStateOf<String?>(null) }
    val errors = if(!submitted) emptyMap() else when(screen) {
        "signup" -> Validation.signupErrors(email,password,confirm,nickname,agree)
        "completeSignup" -> Validation.profileErrors(nickname,agree)
        else -> emptyMap()
    }
    val inputsEnabled = !vm.busy && !selectingGoogle
    Text("✦",fontSize=48.sp,color=Purple);Heading(if(screen=="login")"좋아하는 마음이\n모이는 곳, 최애.zip" else titles[screen]?:"")
    if(screen=="login"){
        OutlinedButton(onClick={
            selectingGoogle = true; googleNotice = "Google 계정을 선택해 주세요."; vm.error = null
            scope.launch {
                try {
                    // Obtain the credential before opening the app's busy Dialog.
                    val credential = googleCredential(context)
                    googleNotice = null
                    vm.work { vm.googleLogin(credential.idToken) }
                } catch(e: kotlinx.coroutines.CancellationException) {
                    googleNotice = "Google 로그인을 취소했어요. 다시 시도할 수 있어요."
                } catch(e: Exception) {
                    googleNotice = authErrorMessage(e)
                } finally { selectingGoogle = false }
            }
        },enabled=inputsEnabled,modifier=Modifier.fillMaxWidth().heightIn(min=52.dp),shape=RoundedCornerShape(16.dp)){Text("Google로 계속하기")}
        googleNotice?.let { Text(it,color=MaterialTheme.colorScheme.primary) }
        Caption("또는 이메일로 로그인")
    }
    if(screen in listOf("signup","completeSignup")) Field(nickname,"닉네임 (2~20자)",{nickname=it},error=errors["nickname"])
    if(screen in listOf("login","signup","reset")) Field(email,"이메일",{email=it},error=errors["email"])
    if(screen in listOf("login","signup","newPassword")) Field(password,"비밀번호${if(screen!="login")" (8~64자)" else ""}",{password=it},password=true,error=errors["password"])
    if(screen in listOf("signup","newPassword")) Field(confirm,"비밀번호 확인",{confirm=it},password=true,error=errors["confirm"])
    if(screen in listOf("signup","completeSignup")) {
        Panel {Text("서비스 이용약관 · 개인정보 처리방침",fontWeight=FontWeight.Bold);Caption("기록은 비공개, 라운지의 닉네임과 글은 공개됩니다. 서비스 제공을 위해 계정·프로필·텍스트 콘텐츠를 Firebase에 저장합니다. 탈퇴 시 데이터 삭제를 요청할 수 있습니다.");Caption("개발용 약관 요약입니다. 실제 서비스 출시 전 운영자의 약관 및 개인정보 처리방침 확정이 필요합니다.")}
        Row(Modifier.fillMaxWidth().toggleable(value=agree,role=Role.Checkbox,onValueChange={agree=it}),verticalAlignment=Alignment.CenterVertically){Checkbox(agree,null);Text("[필수] 이용약관 및 개인정보 처리에 동의")}
        errors["agree"]?.let { Text(it,color=MaterialTheme.colorScheme.error) }
    }
    Primary(when(screen){"login"->"로그인";"reset"->"재설정 링크 발송";"newPassword"->"비밀번호 변경";else->"가입 완료"},inputsEnabled){
        submitted = true
        if(screen=="signup" && Validation.signupErrors(email,password,confirm,nickname,agree).isNotEmpty()) {
            FirebaseTrace.event("auth.signup.validation", "REJECTED", "fields=${Validation.signupErrors(email,password,confirm,nickname,agree).keys.joinToString(",")}")
            return@Primary
        }
        if(screen=="completeSignup" && Validation.profileErrors(nickname,agree).isNotEmpty()) {
            FirebaseTrace.event("auth.profile.validation", "REJECTED", "fields=${Validation.profileErrors(nickname,agree).keys.joinToString(",")}")
            return@Primary
        }
        when(screen){"login"->vm.login(email,password);"signup"->vm.signup(email,password,confirm,nickname,agree);"completeSignup"->vm.completeSignup(nickname,agree);"reset"->vm.resetPassword(email);"newPassword"->vm.newPassword(vm.route.id,password,confirm)}
    }
    if(submitted && errors.isNotEmpty()) Text(errors.values.joinToString("\n"),color=MaterialTheme.colorScheme.error)
    vm.error?.let { Text(it,color=MaterialTheme.colorScheme.error) }
    if(screen=="login") {MenuRow("이메일로 회원가입"){vm.navigate("signup")};MenuRow("비밀번호를 잊으셨나요?"){vm.navigate("reset")};TextButton(onClick={vm.backNow()},modifier=Modifier.fillMaxWidth()){Text("둘러보기")}}
    if(screen=="reset") Caption("계정 등록 여부와 관계없이 같은 안내를 표시해요. 메일 링크는 Firebase의 안전한 재설정 화면에서 열립니다.")
    if(screen=="completeSignup")TextButton(onClick={vm.cancelSocial()}){Text("취소하고 둘러보기")}
}
@Composable fun Notifications(vm: AppViewModel) {
    if(!vm.signedIn){Guest(vm,"내 알림을 확인해 보세요");return}
    MenuRow("알림 설정"){vm.navigate("preferences")}
    Caption("읽지 않은 알림 ${vm.items.count{!it.bool("read")}}개 · 최근 30일")
    vm.items.forEach { n -> Panel(color=if(n.bool("read")) Color(0xFFF6F3F9) else Lavender,onClick={vm.openNotification(n)}) {Text(n.str("title"),fontWeight=FontWeight.Bold);Text(n.str("body"));Caption(dateTime(n.num("createdAt")))} }
    if(vm.items.isEmpty()&&!vm.loading&&vm.error==null)EmptyState("아직 도착한 알림이 없어요","최애 일정과 라운지 소식을 여기서 확인하세요.");More(vm)
}
@Composable fun Preferences(vm: AppViewModel) {
    val context=LocalContext.current
    val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){vm.syncDevice()}
    val allowed=Build.VERSION.SDK_INT<33||context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)==android.content.pm.PackageManager.PERMISSION_GRANTED
    Panel { Text(if(allowed)"기기 알림이 허용되어 있어요" else "기기 알림이 꺼져 있어요",fontWeight=FontWeight.Bold);Caption("알림 권한을 허용하면 Firebase 콘솔에서 보낸 알림을 받을 수 있어요.")
        TextButton(onClick={if(Build.VERSION.SDK_INT>=33&&!allowed)permission.launch(Manifest.permission.POST_NOTIFICATIONS) else context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,context.packageName))}){Text("기기 알림 설정")}}
    // Spark: automatic push preferences disabled.
/*
    listOf("schedule" to "일정 안내","comment" to "댓글","like" to "좋아요","memory" to "기록 회고 (별도 동의)").forEach{(key,label)->Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Text(label,Modifier.weight(1f));Switch(checked=vm.data.bool(key),onCheckedChange={vm.preference(key,it)},enabled=!vm.busy&&!vm.loading)}}
    Caption("댓글·좋아요·회고 푸시는 한국 시각 22:00~08:00에 보내지 않아요.")
*/
    Caption("Firebase 콘솔에서 보낸 알림은 기기 알림 권한에 따라 표시됩니다.")
}
@Composable fun Profile(vm: AppViewModel) {
    if(vm.loading)return
    var nickname by rememberSaveable{mutableStateOf(vm.data.str("nickname"))};var bio by rememberSaveable{mutableStateOf(vm.data.str("bio"))}
    Text("✦",fontSize=60.sp,color=Purple);Caption("닉네임과 소개는 팬라운지에 공개돼요. 이메일은 공개하지 않아요.")
    Field(nickname,"닉네임 (2~20자)",{nickname=it;vm.dirty=true});Field(bio,"소개 (최대 100자)",{bio=it;vm.dirty=true},multiline=true);Primary("저장",!vm.busy){vm.profile(nickname,bio)}
}
@Composable fun Blocks(vm: AppViewModel) {
    var target by remember{mutableStateOf<Doc?>(null)}
    if(vm.items.isEmpty()&&!vm.loading&&vm.error==null)EmptyState("차단한 사용자가 없어요","차단한 사용자는 이곳에서 관리할 수 있어요.")
    vm.items.forEach{u->MenuRow(u.str("nickname"),"차단 해제"){target=u}}
    target?.let{u->AlertDialog(onDismissRequest={target=null},title={Text("차단을 해제할까요?")},text={Text("내 차단만 해제됩니다. 상대가 차단 중이면 서로의 글은 보이지 않아요.")},confirmButton={TextButton(onClick={target=null;vm.block(u.str("id"),false)}){Text("해제")}},dismissButton={TextButton(onClick={target=null}){Text("취소")}})}
}
@Composable fun Report(vm: AppViewModel) {
    var reason by rememberSaveable{mutableStateOf("스팸")};var detail by rememberSaveable{mutableStateOf("")};var confirm by remember{mutableStateOf("")}
    Heading("안전한 팬라운지를 함께 만들어요")
    Chips(listOf("스팸","욕설","개인정보","권리침해","기타"),reason){reason=it}
    Field(detail,"상세 설명 (최대 500자)",{if(it.length<=500)detail=it},multiline=true)
    Primary("신고하기",!vm.busy){confirm="report"}
    Caption("신고와 차단은 별도로 동작해요. 신고만으로 자동 숨김되지 않아요.")
    OutlinedButton(onClick={confirm="block"},enabled=!vm.busy,modifier=Modifier.fillMaxWidth()){Text("이 사용자 차단")}
    if(confirm.isNotBlank())AlertDialog(onDismissRequest={confirm=""},title={Text(if(confirm=="block")"사용자를 차단할까요?" else "신고를 접수할까요?")},text={Text(if(confirm=="block")"서로의 글과 댓글, 반응이 제한됩니다." else "운영자가 신고 내용을 검토합니다.")},confirmButton={TextButton(onClick={val action=confirm;confirm="";if(action=="block")vm.block(vm.reportTarget.str("userId"),true) else vm.report(reason,detail)}){Text("확인")}},dismissButton={TextButton(onClick={confirm=""}){Text("취소")}})
}
@Composable fun DeleteAccount(vm: AppViewModel) {
    val context=LocalContext.current;var password by remember{mutableStateOf("")};var agree by remember{mutableStateOf(false)};var confirm by remember{mutableStateOf(false)}
    Heading("탈퇴 전에 확인해 주세요")
    Panel(color=Color(0xFFFBECEF)){Text("개인 기록·프로필·내 댓글과 좋아요를 삭제하고, 내 게시글 본문을 지워 비공개 처리해요.",fontWeight=FontWeight.Bold);Text("탈퇴 후에는 복구할 수 없습니다. 본인 확인 후 데이터 정리를 완료하고 로그인 계정을 삭제합니다. 연결 오류가 나면 앱을 종료하지 말고 다시 시도해 주세요.")}
    val google=Backend.auth.currentUser?.providerData?.any{it.providerId=="google.com"}==true
    if(!google)Field(password,"본인 확인 · 현재 비밀번호",{password=it},password=true)
    Row(verticalAlignment=Alignment.CenterVertically){Checkbox(agree,{agree=it});Text("안내를 확인했으며 탈퇴에 동의합니다.")}
    Primary(if(google)"Google 본인 확인 후 탈퇴" else "본인 확인 후 탈퇴",agree&&!vm.busy){confirm=true}
    if(confirm)AlertDialog(onDismissRequest={confirm=false},title={Text("정말 탈퇴하시겠어요?")},text={Text("삭제한 데이터는 복구할 수 없습니다.")},confirmButton={TextButton(onClick={confirm=false;vm.deleteAccount(password,if(google) suspend {val c=googleCredential(context);Backend.auth.currentUser!!.reauthenticate(GoogleAuthProvider.getCredential(c.idToken,null)).await(); Unit} else null)}){Text("탈퇴")}},dismissButton={TextButton(onClick={confirm=false}){Text("취소")}})
}
