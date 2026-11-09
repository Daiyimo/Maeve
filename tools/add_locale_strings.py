# -*- coding: utf-8 -*-
"""把 stepclaw_*（上个提交遗留缺失）和 amoo_intercept_*（本次新增）补齐到各语言目录。
只往缺失这两个键组的语言文件里插,不动 values/ 与 values-zh（已有）。"""
import io, os, re

RES = r"E:\project\daiyimo\Maeve\app\src\main\res"

# key -> 各语言译文（不含 amoo/stepclaw 键本身已有 zh/en）
T = {
"values-b+zh+Hant": {
 "stepclaw_title": "快速瞭解 stepclaw",
 "stepclaw_what": "stepclaw 是跑在這台裝置上的 OpenClaw 閘道器：它作為 MCP 用戶端連入 Maeve，把電腦或雲端下發的任務交給手機執行，地址固定為 http://127.0.0.1:%1$d/mcp。",
 "stepclaw_status_title": "stepclaw 狀態",
 "stepclaw_connected": "已連入，最近一次請求 %1$s",
 "stepclaw_listening": "閘道器已在 http://127.0.0.1:%1$d/mcp 監聽，等 stepclaw 接入",
 "stepclaw_clients": "已有 %1$d 個用戶端連入，等第一個請求",
 "stepclaw_off": "未監聽。到下方「進階設定」打開「Wi-Fi 和私有網路」或「USB」開關，本機回環地址才會被監聽。",
 "stepclaw_copy_config": "複製閘道器設定",
 "stepclaw_new_device": "換裝置或重裝 Maeve：連接埠不變，但權杖會重新隨機產生，需要把新權杖重新配到閘道器的 mcp.servers.maeve。",
 "amoo_intercept_title": "截獲 amoo 聊天任務",
 "amoo_intercept_sub": "開啟後，在 amoo 聊天裡發送的每則訊息，都會在 Maeve 裡新增一個任務並執行。",
 "amoo_intercept_note": "需要保持無障礙服務開啟。訊息仍會照常發給 amoo 的雲端——Maeve 只是在本機並行做一遍。任務記錄在「任務」頁，可隨時取消。",
},
"values-ja": {
 "stepclaw_title": "stepclaw を知る",
 "stepclaw_what": "stepclaw はこの端末で動く OpenClaw ゲートウェイです。MCP クライアントとして Maeve に接続し、PC やクラウドから届いたタスクをこの端末で実行します。アドレスは常に http://127.0.0.1:%1$d/mcp です。",
 "stepclaw_status_title": "stepclaw の状態",
 "stepclaw_connected": "接続済み。最後のリクエスト %1$s",
 "stepclaw_listening": "ゲートウェイが http://127.0.0.1:%1$d/mcp で待機中、stepclaw の接続を待っています",
 "stepclaw_clients": "%1$d 件のクライアントが接続済み、最初のリクエストを待っています",
 "stepclaw_off": "待機していません。下の「詳細設定」で「Wi-Fi とプライベートネットワーク」または「USB」をオンにすると、ループバックアドレスが待機します。",
 "stepclaw_copy_config": "ゲートウェイ設定をコピー",
 "stepclaw_new_device": "別の端末に移るか Maeve を再インストールすると、ポートは変わりませんがトークンは毎回ランダムになります。新しいトークンをゲートウェイの mcp.servers.maeve に設定し直してください。",
 "amoo_intercept_title": "amoo のチャットタスクを傍受",
 "amoo_intercept_sub": "オンにすると、amoo のチャットで送ったメッセージがすべて Maeve のタスクになり、ここで実行されます。",
 "amoo_intercept_note": "ユーザー補助サービスをオンにしておく必要があります。メッセージはこれまで通り amoo のクラウドにも届きます——Maeve はこの端末で並行して実行するだけです。タスクは「タスク」ページに記録され、いつでもキャンセルできます。",
},
"values-ko": {
 "stepclaw_title": "stepclaw 알아보기",
 "stepclaw_what": "stepclaw는 이 기기에서 실행되는 OpenClaw 게이트웨이입니다. MCP 클라이언트로 Maeve에 접속해 PC나 클라우드에서 내려온 작업을 이 폰에서 수행합니다. 주소는 항상 http://127.0.0.1:%1$d/mcp 입니다.",
 "stepclaw_status_title": "stepclaw 상태",
 "stepclaw_connected": "연결됨. 마지막 요청 %1$s",
 "stepclaw_listening": "게이트웨이가 http://127.0.0.1:%1$d/mcp 에서 대기 중, stepclaw 접속을 기다리는 중",
 "stepclaw_clients": "클라이언트 %1$d 개 연결됨, 첫 요청 대기 중",
 "stepclaw_off": "대기하지 않습니다. 아래 '고급 설정'에서 'Wi-Fi 및 사설 네트워크' 또는 'USB'를 켜야 루프백 주소가 열립니다.",
 "stepclaw_copy_config": "게이트웨이 설정 복사",
 "stepclaw_new_device": "기기를 바꾸거나 Maeve를 다시 설치하면 포트는 그대로지만 토큰은 새로 랜덤 생성됩니다. 새 토큰을 게이트웨이의 mcp.servers.maeve에 다시 등록하세요.",
 "amoo_intercept_title": "amoo 채팅 작업 가로채기",
 "amoo_intercept_sub": "켜면 amoo 채팅에서 보낸 메시지가 모두 Maeve의 작업이 되어 여기서 실행됩니다.",
 "amoo_intercept_note": "접근성 서비스를 켜둬야 합니다. 메시지는 평소처럼 amoo 클라우드로도 전송되고, Maeve는 이 폰에서 병렬로 실행할 뿐입니다. 작업은 '작업' 페이지에 기록되며 언제든 취소할 수 있습니다.",
},
"values-es": {
 "stepclaw_title": "Conoce stepclaw",
 "stepclaw_what": "stepclaw es la puerta de enlace OpenClaw que funciona en este dispositivo. Se conecta a Maeve como cliente MCP y ejecuta en este teléfono las tareas enviadas desde el PC o la nube, siempre en http://127.0.0.1:%1$d/mcp.",
 "stepclaw_status_title": "Estado de stepclaw",
 "stepclaw_connected": "Conectado. Última solicitud %1$s",
 "stepclaw_listening": "La puerta de enlace escucha en http://127.0.0.1:%1$d/mcp, esperando a stepclaw",
 "stepclaw_clients": "%1$d cliente(s) conectado(s), esperando la primera solicitud",
 "stepclaw_off": "No está escuchando. Activa «Wi-Fi y redes privadas» o «USB» en los ajustes avanzados de abajo: la dirección de bucle local necesita uno de los dos.",
 "stepclaw_copy_config": "Copiar configuración de la puerta de enlace",
 "stepclaw_new_device": "¿Cambias de dispositivo o reinstalas Maeve? El puerto no cambia, pero el token se genera al azar en cada instalación: vuelve a añadirlo a mcp.servers.maeve de la puerta de enlace.",
 "amoo_intercept_title": "Interceptar tareas del chat de amoo",
 "amoo_intercept_sub": "Si está activado, cada mensaje que envíes en el chat de amoo también se convierte en una tarea de Maeve y se ejecuta aquí.",
 "amoo_intercept_note": "Requiere el servicio de accesibilidad activado. Los mensajes siguen llegando a la nube de amoo como siempre: Maeve solo los ejecuta en este teléfono en paralelo. Las tareas aparecen en la página de tareas y se pueden cancelar en cualquier momento.",
},
"values-pt": {
 "stepclaw_title": "Conheça o stepclaw",
 "stepclaw_what": "O stepclaw é o gateway OpenClaw que roda neste aparelho. Ele se conecta ao Maeve como cliente MCP e executa neste celular as tarefas enviadas pelo computador ou pela nuvem, sempre em http://127.0.0.1:%1$d/mcp.",
 "stepclaw_status_title": "Status do stepclaw",
 "stepclaw_connected": "Conectado. Última solicitação %1$s",
 "stepclaw_listening": "O gateway está escutando em http://127.0.0.1:%1$d/mcp, aguardando o stepclaw",
 "stepclaw_clients": "%1$d cliente(s) conectado(s), aguardando a primeira solicitação",
 "stepclaw_off": "Não está escutando. Ative «Wi-Fi e redes privadas» ou «USB» nas configurações avançadas abaixo — o endereço de loopback precisa de um dos dois.",
 "stepclaw_copy_config": "Copiar configuração do gateway",
 "stepclaw_new_device": "Mudou de aparelho ou reinstalou o Maeve? A porta continua a mesma, mas o token é gerado aleatoriamente em cada instalação — adicione-o novamente em mcp.servers.maeve do gateway.",
 "amoo_intercept_title": "Interceptar tarefas do chat do amoo",
 "amoo_intercept_sub": "Quando ativado, cada mensagem enviada no chat do amoo também vira uma tarefa no Maeve e é executada aqui.",
 "amoo_intercept_note": "Precisa do serviço de acessibilidade ligado. As mensagens continuam indo para a nuvem do amoo como sempre — o Maeve apenas as executa neste celular em paralelo. As tarefas ficam na página de tarefas e podem ser canceladas a qualquer momento.",
},
"values-ru": {
 "stepclaw_title": "Знакомство со stepclaw",
 "stepclaw_what": "stepclaw — это шлюз OpenClaw, работающий на этом устройстве. Он подключается к Maeve как MCP-клиент и передаёт задачи с компьютера или из облака этому телефону, всегда по адресу http://127.0.0.1:%1$d/mcp.",
 "stepclaw_status_title": "Статус stepclaw",
 "stepclaw_connected": "Подключено. Последний запрос %1$s",
 "stepclaw_listening": "Шлюз слушает http://127.0.0.1:%1$d/mcp и ждёт stepclaw",
 "stepclaw_clients": "Подключено клиентов: %1$d, ожидаем первый запрос",
 "stepclaw_off": "Не слушает. Включите «Wi-Fi и частные сети» или «USB» в расширенных настройках ниже — для адреса обратной петли нужен один из них.",
 "stepclaw_copy_config": "Скопировать конфигурацию шлюза",
 "stepclaw_new_device": "Сменили устройство или переустановили Maeve? Порт не меняется, но токен создаётся заново при каждой установке — добавьте его в mcp.servers.maeve шлюза повторно.",
 "amoo_intercept_title": "Перехват задач из чата amoo",
 "amoo_intercept_sub": "Когда включено, каждое сообщение из чата amoo становится задачей в Maeve и выполняется здесь.",
 "amoo_intercept_note": "Нужна включённая служба специальных возможностей. Сообщения по-прежнему уходят в облако amoo как обычно — Maeve просто выполняет их на этом телефоне параллельно. Задачи попадают на страницу задач и их можно отменить в любой момент.",
},
"values-ar": {
 "stepclaw_title": "تعرّف على stepclaw",
 "stepclaw_what": "stepclaw هو بوابة OpenClaw التي تعمل على هذا الجهاز. تتصل بـ Maeve كعميل MCP وتنفّذ المهام الواردة من الحاسوب أو السحابة على هذا الهاتف، دائمًا على http://127.0.0.1:%1$d/mcp.",
 "stepclaw_status_title": "حالة stepclaw",
 "stepclaw_connected": "متصل. آخر طلب %1$s",
 "stepclaw_listening": "البوابة تستمع على http://127.0.0.1:%1$d/mcp في انتظار stepclaw",
 "stepclaw_clients": "%1$d عميل متصل، في انتظار أول طلب",
 "stepclaw_off": "لا تستمع. شغّل «Wi-Fi والشبكات الخاصة» أو «USB» من الإعدادات المتقدمة أدناه — عنوان الاسترجاع المحلي يحتاج أحدهما.",
 "stepclaw_copy_config": "نسخ إعدادات البوابة",
 "stepclaw_new_device": "انتقلت إلى جهاز آخر أو أعدت تثبيت Maeve؟ المنفذ لا يتغيّر، لكن الرمز يُنشأ عشوائيًا عند كل تثبيت — أعد إضافته إلى mcp.servers.maeve في البوابة.",
 "amoo_intercept_title": "اعتراض مهام دردشة amoo",
 "amoo_intercept_sub": "عند التشغيل، كل رسالة ترسلها في دردشة amoo تصبح مهمة في Maeve وتُنفّذ هنا.",
 "amoo_intercept_note": "يتطلّب تشغيل خدمة إمكانية الوصول. الرسائل تصل إلى سحابة amoo كالمعتاد — Maeve ينفّذها على هذا الهاتف بالتوازي فقط. تظهر المهام في صفحة المهام ويمكن إلغاؤها في أي وقت.",
},
"values-in": {
 "stepclaw_title": "Mengenal stepclaw",
 "stepclaw_what": "stepclaw adalah gateway OpenClaw yang berjalan di perangkat ini. Ia terhubung ke Maeve sebagai klien MCP dan mengeksekusi tugas dari komputer atau cloud di ponsel ini, selalu di http://127.0.0.1:%1$d/mcp.",
 "stepclaw_status_title": "Status stepclaw",
 "stepclaw_connected": "Tersambung. Permintaan terakhir %1$s",
 "stepclaw_listening": "Gateway mendengarkan di http://127.0.0.1:%1$d/mcp, menunggu stepclaw",
 "stepclaw_clients": "%1$d klien tersambung, menunggu permintaan pertama",
 "stepclaw_off": "Tidak mendengarkan. Nyalakan \"Wi-Fi dan jaringan privat\" atau \"USB\" di pengaturan lanjutan di bawah — alamat loopback memerlukan salah satunya.",
 "stepclaw_copy_config": "Salin konfigurasi gateway",
 "stepclaw_new_device": "Pindah perangkat atau memasang ulang Maeve? Port tetap sama, tetapi token dibuat acak setiap instalasi — tambahkan lagi ke mcp.servers.maeve gateway.",
 "amoo_intercept_title": "Intersepsi tugas chat amoo",
 "amoo_intercept_sub": "Saat aktif, setiap pesan yang kamu kirim di chat amoo juga menjadi tugas di Maeve dan dijalankan di sini.",
 "amoo_intercept_note": "Membutuhkan layanan aksesibilitas aktif. Pesan tetap terkirim ke cloud amoo seperti biasa — Maeve hanya menjalankannya di ponsel ini secara paralel. Tugas muncul di halaman Tugas dan bisa dibatalkan kapan saja.",
},
}

def esc(s):
    # aapt2 要求 Apostrophe not preceded by \
    return s.replace("'", "\\'")

for locale, kv in T.items():
    path = os.path.join(RES, locale, "strings_mcp.xml")
    with io.open(path, encoding="utf-8") as f:
        x = f.read()
    have = set(re.findall(r'<string name="([^"]+)"', x))
    add = {k: v for k, v in kv.items() if k not in have}
    if not add:
        print(locale, "already complete")
        continue
    block = "\n".join('    <string name="%s">%s</string>' % (k, esc(v)) for k, v in add.items())
    assert "</resources>" in x, locale
    x = x.replace("</resources>", block + "\n</resources>")
    with io.open(path, "w", encoding="utf-8", newline="\n") as f:
        f.write(x)
    print(locale, "added", len(add), "keys")
