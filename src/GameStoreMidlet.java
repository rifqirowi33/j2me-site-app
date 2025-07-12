import javax.microedition.midlet.*;
import javax.microedition.io.Connector;
import javax.microedition.io.file.FileConnection;
import javax.microedition.io.HttpConnection;
import javax.microedition.io.file.FileSystemRegistry;
import javax.microedition.lcdui.*;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Vector;

public class GameStoreMidlet extends MIDlet implements CommandListener {
    private Display display;
    private Canvas splashCanvas, loadingCanvas, welcomeCanvas, refreshCanvas, popupCanvas;
    private GameListCanvas currentGameCanvas;
    private GameDetailCanvas lastDetailCanvas;
    private List gameList;
    private Image[] gameIcons;
    private Displayable previousScreen;
    private Command cmdLanjut, cmdOpenLink1, cmdOpenLink2;
    private Command cmdExit, cmdRetry, cmdMenu;
    private boolean firstInstall = true;
    private String[][] gameListData = null;
    private String[][] gameData;
    private String pendingDownloadURL;
    private String pendingDownloadTitle;
    private String pendingDownloadFileName;


    public void startApp() {
        display = Display.getDisplay(this);
        showSplashScreen();
    }

    private void showSplashScreen() {
        splashCanvas = new SplashCanvas();
        display.setCurrent(splashCanvas);
        new Thread() {
            public void run() {
                try { Thread.sleep(2000); } catch (InterruptedException e) {}
                showLoadingScreen();
            }
        }.start();
    }

    private void showLoadingScreen() {
    loadingCanvas = new LoadingCanvas();
    display.setCurrent(loadingCanvas);

    new Thread() {
    public void run() {
        ((LoadingCanvas) loadingCanvas).setStatus("Menghubungkan ke server...");
        try {
            // ⬇ Tambahkan ?nocache untuk menghindari cache
            String gamelistUrl = "https://java.repp.my.id/gamelist.txt?nocache=" + System.currentTimeMillis();
            InputStream is = Connector.openInputStream(gamelistUrl);
            
            StringBuffer sb = new StringBuffer();
            int ch;
            while ((ch = is.read()) != -1) sb.append((char) ch);
            is.close();

            ((LoadingCanvas) loadingCanvas).setStatus("Memuat daftar game...");
            String[] lines = splitLines(sb.toString());
            gameListData = new String[lines.length][];
            for (int i = 0; i < lines.length; i++) {
                gameListData[i] = splitGameEntry(lines[i]);
            }

            System.out.println("GAMELIST RAW:\n" + sb.toString());

            // Siapkan array untuk icon
            gameIcons = new Image[gameListData.length];

            // Mulai mengunduh icon-icon
            for (int i = 0; i < gameListData.length; i++) {
                System.out.println("Data ke-" + i + " jumlah kolom: " + gameListData[i].length);

                if (gameListData[i].length >= 7) {
                    String iconUrl = gameListData[i][6]; // kolom 6 = URL icon
                    try {
                        System.out.println("Downloading icon from: " + iconUrl);
                        ((LoadingCanvas) loadingCanvas).setStatus("Memuat Icon: " + (i + 1) + "/" + gameListData.length);
                        
                        HttpConnection conn = (HttpConnection) Connector.open(iconUrl);
                        conn.setRequestMethod(HttpConnection.GET);
                        InputStream iconStream = conn.openInputStream();

                        ByteArrayOutputStream baos = new ByteArrayOutputStream();
                        int b;
                        while ((b = iconStream.read()) != -1) baos.write(b);
                        iconStream.close(); conn.close();

                        gameIcons[i] = Image.createImage(baos.toByteArray(), 0, baos.size());
                        if (gameIcons[i] != null) {
                            System.out.println("Image created: " + gameIcons[i].getWidth() + "x" + gameIcons[i].getHeight());
                        } else {
                            System.out.println("Image is null after createImage!");
                        }
                        System.out.println("Berhasil buat image ke-" + i + ": " + iconUrl + " Ukuran: " + gameIcons[i].getWidth() + "x" + gameIcons[i].getHeight());
                    } catch (Exception ex) {
                        gameIcons[i] = null;
                        System.out.println("Gagal memuat ikon: " + iconUrl + " => " + ex.toString());
                    }
                } else {
                    gameIcons[i] = null;
                    System.out.println("Data kurang dari 7 kolom untuk game ke-" + i);
                } 

                ((LoadingCanvas) loadingCanvas).setProgress((i + 1) * 100 / gameListData.length);
            }

            // Lanjutkan ke tampilan berikut
            if (firstInstall) showWelcomeScreen();
            else showGameListFrom(gameListData, gameIcons);

        } catch (Exception e) {
            ((LoadingCanvas) loadingCanvas).setStatus("Gagal terhubung!");
            loadingCanvas.addCommand(cmdRetry = new Command("Coba Lagi", Command.OK, 1));
            loadingCanvas.setCommandListener(GameStoreMidlet.this);
        }
    }
}.start();

}

    private void refreshGameList() {
    refreshCanvas = new RefreshCanvas();
    display.setCurrent(refreshCanvas);

    new Thread() {
        public void run() {
            try {
                InputStream is = Connector.openInputStream("https://java.repp.my.id/gamelist.txt");
                StringBuffer sb = new StringBuffer();
                int ch;
                while ((ch = is.read()) != -1) {
                    sb.append((char) ch);
                }
                is.close();
                String[] lines = splitLines(sb.toString());
                gameListData = new String[lines.length][];
                for (int i = 0; i < lines.length; i++) {
                    gameListData[i] = splitGameEntry(lines[i]);
                }

                showGameListFrom(gameListData);
            } catch (Exception e) {
                showGameListFrom(gameListData);
            }
        }
    }.start();
}

    private String[] splitLines(String text) {
        Vector lines = new Vector();
        int len = text.length(), start = 0;
        for (int i = 0; i < len; i++) {
            char c = text.charAt(i);
            if (c == '\r' || c == '\n') {
                int end = i;
                if (c == '\r' && i + 1 < len && text.charAt(i + 1) == '\n') i++;
                String line = text.substring(start, end).trim();
                if (line.length() > 0) lines.addElement(line);
                start = i + 1;
            }
        }
        if (start < len) {
            String line = text.substring(start).trim();
            if (line.length() > 0) lines.addElement(line);
        }
        String[] arr = new String[lines.size()];
        lines.copyInto(arr);
        return arr;
    }

    private String[] splitGameEntry(String line) {
    String[] parts = new String[7];
    int start = 0, end;
    for (int i = 0; i < 6; i++) {
        end = line.indexOf('|', start);
        if (end == -1) { parts[i] = ""; break; }
        else { parts[i] = line.substring(start, end).trim(); start = end + 1; }
    }
    parts[6] = (start < line.length()) ? line.substring(start).trim() : "";
    return parts;
}

private void showGameListFrom(String[][] data, Image[] icons) {
    this.gameListData = data; // simpan supaya bisa diakses ulang
    this.gameIcons = icons;   // simpan icon-icon game
    currentGameCanvas = new GameListCanvas(data, icons); // Canvas constructor harus 2 argumen
    display.setCurrent(currentGameCanvas);
}

private void showGameListFrom(String[][] data) {
    System.out.println("[WARN] showGameListFrom(data) dipanggil tanpa ikon!");
    showGameListFrom(data, this.gameIcons != null ? this.gameIcons : new Image[data.length]);
}

private void showDetailScreen(final String[] data, final Image icon) {
    final Canvas loading = new DetailLoadingCanvas();
    display.setCurrent(loading);

    new Thread() {
        public void run() {
            try { Thread.sleep(100); } catch (Exception e) {}

            Image cover = null;
            try {
                if (data.length >= 6 && data[5] != null && data[5].startsWith("http")) {
                    HttpConnection conn = (HttpConnection) Connector.open(data[5]);
                    conn.setRequestMethod(HttpConnection.GET);
                    InputStream is = conn.openInputStream();

                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    int b;
                    while ((b = is.read()) != -1) baos.write(b);
                    is.close(); conn.close();

                    Image temp = Image.createImage(baos.toByteArray(), 0, baos.size());
                    if (temp.getWidth() < loading.getWidth() && temp.getHeight() < loading.getHeight() / 2) {
                        cover = temp;
                    }
                }
            } catch (Exception e) {
                System.out.println("Gagal ambil cover: " + e.toString());
            }

            final Image finalCover = cover;
            final GameDetailCanvas detailCanvas = new GameDetailCanvas(data, finalCover);
            lastDetailCanvas = detailCanvas;
            display.setCurrent(detailCanvas);
        }
    }.start();
}

    private void showPopup(String title, String content) {
    previousScreen = display.getCurrent(); // Simpan sebelum tampilkan popup
    popupCanvas = new PopupCanvas(title, content);
    display.setCurrent(popupCanvas);
    }

    private void showWelcomeScreen() {
        welcomeCanvas = new WelcomeCanvas();
        cmdLanjut = new Command("Lanjut", Command.OK, 1);
        welcomeCanvas.addCommand(cmdLanjut);     // lanjut
        
        welcomeCanvas.setCommandListener(this);
        display.setCurrent(welcomeCanvas);
    }

    public void commandAction(Command c, Displayable d) {
        if (c == cmdLanjut) {
            firstInstall = false;
            if (gameListData != null) showGameListFrom(gameListData);
            else showLoadingScreen();
        } else if (c == cmdOpenLink1) {
            try { platformRequest("http://java.repp.my.id"); } catch (Exception e) {}
        } else if (c == cmdOpenLink2) {
            try { platformRequest("http://www.repp.my.id"); } catch (Exception e) {}
        } else if (c == cmdRetry) {
            showLoadingScreen();
        } else if (c == cmdMenu && (d == gameList || d == currentGameCanvas)) {
            showPopup("Menu", "1. Segarkan\n2. Tentang Aplikasi");
        } else if (c == cmdExit && (d == gameList || d == currentGameCanvas)) {
            showPopup("Konfirmasi", "Yakin keluar dari aplikasi?");
        } else if (c == List.SELECT_COMMAND && d == currentGameCanvas) {
            int idx = gameList.getSelectedIndex();
            if (idx >= 0 && gameData != null && gameData.length > idx) {
                String[] data = gameData[idx];
                String deskripsi = data[4];
                showPopup(data[0], deskripsi);
            }
        }
    }

    public void pauseApp() {}

    public void destroyApp(boolean unconditional) {}

    public void backToDetail() {
    if (lastDetailCanvas != null) {
        display.setCurrent(lastDetailCanvas);
    }
}

class SplashCanvas extends Canvas {
        private Image logo;
        public SplashCanvas() {
            try { logo = Image.createImage("/logo.png"); } catch (Exception e) {}
        }
        protected void paint(Graphics g) {
            int w = getWidth(), h = getHeight();
            g.setColor(0); g.fillRect(0, 0, w, h);
            if (logo != null)
                g.drawImage(logo, (w - logo.getWidth()) / 2, (h - logo.getHeight()) / 2, Graphics.TOP | Graphics.LEFT);
            else {
                g.setColor(0xFFFFFF);
                g.drawString("JAVA.REPP.MY.ID", w / 2, h / 2, Graphics.HCENTER | Graphics.BASELINE);
            }
        }
    }

class LoadingCanvas extends Canvas {
        private int progress = 0;
        private String status = "";
        private Image logo;
        public LoadingCanvas() {
            try { logo = Image.createImage("/splash.png"); } catch (Exception e) {}
        }
        public void setProgress(int p) { progress = p; repaint(); }
        public void setStatus(String s) { status = s; repaint(); }
        protected void paint(Graphics g) {
            int w = getWidth(), h = getHeight();
            g.setColor(0); g.fillRect(0, 0, w, h);
            if (logo != null)
                g.drawImage(logo, (w - logo.getWidth()) / 2, (h - logo.getHeight()) / 2 - 20, Graphics.TOP | Graphics.LEFT);
            g.setColor(0xFFFFFF);
            g.drawString(status, w / 2, h - 50, Graphics.HCENTER | Graphics.TOP);
            int bw = w - 40, bh = 10, x = 20, y = h - 30;
            g.drawRect(x, y, bw, bh);
            g.setColor(0x00AAFF);
            g.fillRect(x + 1, y + 1, (bw - 2) * progress / 100, bh - 2);
        }
    }

class DetailLoadingCanvas extends Canvas {
    private int dots = 0;

    public DetailLoadingCanvas() {
        new Thread() {
            public void run() {
                while (display.getCurrent() == DetailLoadingCanvas.this) {
                    dots = (dots + 1) % 4;
                    repaint();
                    try { Thread.sleep(400); } catch (Exception e) {}
                }
            }
        }.start();
    }

    protected void paint(Graphics g) {
        int w = getWidth(), h = getHeight();
        g.setColor(0);
        g.fillRect(0, 0, w, h);
        g.setColor(0xFFFFFF);
        String loadingText = "Memuat";
        for (int i = 0; i < dots; i++) loadingText += ".";
        g.drawString(loadingText, w / 2, h / 2, Graphics.HCENTER | Graphics.VCENTER);
    }
}

class PopupCanvas extends Canvas {
        private String title, content;
        private boolean isExitPopup;
        private int selectedButton = 0; // 0 = Iya, 1 = Tidak
        private int frame = 0;
        private boolean isMenuPopup;
        private String[] menuOptions;
        private int selectedMenuIndex = 0;

        public PopupCanvas(String t, String c) {
            title = t;
            content = c;
            isExitPopup = title.equals("Konfirmasi") && content.indexOf("Yakin keluar") != -1;
            isMenuPopup = title.equals("Menu");
    
    if (isMenuPopup) {
        menuOptions = new String[] { "Segarkan", "Tentang Aplikasi" };
    }

    new Thread() {
        public void run() {
            while (frame < 8) {
                frame++;
                repaint();
                try { Thread.sleep(60); } catch (Exception e) {}
            }
        }
    }.start();
}
        protected void paint(Graphics g) {
            int w = getWidth(), h = getHeight();
            g.setColor(0); g.fillRect(0, 0, w, h);
            int boxW = w - 40, boxH = h / 2;
            int x = 20, y = (h - boxH) / 2;
            if (frame < 5) y += (5 - frame) * 10;
            g.setColor(0xFFFFFF);
            g.drawRect(x, y, boxW, boxH);
            g.setColor(0x111111);
            g.fillRect(x + 1, y + 1, boxW - 2, boxH - 2);
            g.setColor(0xFFFFFF);
            g.drawString(title, w / 2, y + 10, Graphics.TOP | Graphics.HCENTER);
            Font f = Font.getFont(Font.FACE_SYSTEM, Font.STYLE_PLAIN, Font.SIZE_SMALL);
            g.setFont(f);
            int textY = y + 30;
           if (isMenuPopup) {
    for (int i = 0; i < menuOptions.length; i++) {
        int itemY = textY + i * f.getHeight();
        if (i == selectedMenuIndex) {
            g.setColor(0x00AAFF); // biru
            g.fillRect(x + 5, itemY - 1, boxW - 10, f.getHeight());
            g.setColor(0x000000); // teks hitam
        } else {
            g.setColor(0xFFFFFF);
        }
        g.drawString(menuOptions[i], x + 10, itemY, Graphics.TOP | Graphics.LEFT);
    }
}
 else {
    String[] lines = splitLines(content);
    for (int i = 0; i < lines.length; i++) {
        g.drawString(lines[i], x + 10, textY + i * f.getHeight(), Graphics.TOP | Graphics.LEFT);
    }

    // Tambah petunjuk tombol OK
    if (!isExitPopup && !isMenuPopup) {
        g.setColor(0x888888);
        Font footFont = Font.getFont(Font.FACE_SYSTEM, Font.STYLE_PLAIN, Font.SIZE_SMALL);
        g.setFont(footFont);
    g.drawString("OK", getWidth() / 2, getHeight() - 4, Graphics.BOTTOM | Graphics.HCENTER);
    }
}

    if (isExitPopup) {
    int buttonY = y + boxH - 30;
    int btnW = (boxW - 30) / 2;
    int btnH = 20;

    // Tombol IYA
    g.setColor(selectedButton == 0 ? 0x00AAFF : 0x333333);
    g.fillRect(x + 10, buttonY, btnW, btnH);
    g.setColor(0xFFFFFF);
    g.drawString("Iya", x + 10 + btnW / 2, buttonY + 3, Graphics.HCENTER | Graphics.TOP);

    // Tombol TIDAK
    g.setColor(selectedButton == 1 ? 0x00AAFF : 0x333333);
    g.fillRect(x + 20 + btnW, buttonY, btnW, btnH);
    g.setColor(0xFFFFFF);
    g.drawString("Tidak", x + 20 + btnW + btnW / 2, buttonY + 3, Graphics.HCENTER | Graphics.TOP);
}
        }

     protected void keyPressed(int keyCode) {
    int action = getGameAction(keyCode);

    if (isExitPopup) {
        if (action == LEFT || keyCode == KEY_NUM4) {
            selectedButton = 0;
            repaint();
        } else if (action == RIGHT || keyCode == KEY_NUM6) {
            selectedButton = 1;
            repaint();
        } else if (action == FIRE || keyCode == KEY_NUM5) {
            if (selectedButton == 0) {
                GameStoreMidlet.this.notifyDestroyed();
            } else {
                if (GameStoreMidlet.this.previousScreen != null) {
                    GameStoreMidlet.this.display.setCurrent(GameStoreMidlet.this.previousScreen);
                }
            }
        }
    } else if (isMenuPopup) {
        if (action == UP || keyCode == KEY_NUM2) {
            selectedMenuIndex = (selectedMenuIndex - 1 + menuOptions.length) % menuOptions.length;
            repaint();
        } else if (action == DOWN || keyCode == KEY_NUM8) {
            selectedMenuIndex = (selectedMenuIndex + 1) % menuOptions.length;
            repaint();
        } else if (action == FIRE || keyCode == KEY_NUM5) {
            String selected = menuOptions[selectedMenuIndex];
            if (selected.equals("Segarkan")) {
                GameStoreMidlet.this.refreshGameList();
            } else if (selected.equals("Tentang Aplikasi")) {
                GameStoreMidlet.this.showPopup("Tentang Aplikasi", 
                    "Game Store oleh REPP\n\nVersi:\n\n1.0\n\nSitus:\njava.repp.my.id\n\n© 2025 REPP");
            }
        } else if (action == LEFT || action == -11) {
            if (GameStoreMidlet.this.previousScreen != null) {
                GameStoreMidlet.this.display.setCurrent(GameStoreMidlet.this.previousScreen);
            }
        }
    } else {
    // Popup biasa: tombol kembali
    if (action == FIRE || action == LEFT || action == RIGHT || action == -11 || keyCode == KEY_NUM5 || keyCode == KEY_POUND) {
        if (GameStoreMidlet.this.previousScreen != null) {
            GameStoreMidlet.this.display.setCurrent(GameStoreMidlet.this.previousScreen);
        } else if (GameStoreMidlet.this.currentGameCanvas != null) {
            GameStoreMidlet.this.display.setCurrent(GameStoreMidlet.this.currentGameCanvas);
        }
    }
}
}
}

class RefreshCanvas extends Canvas {
    private int dotCount = 0;
    private long lastUpdateTime = 0;

    public RefreshCanvas() {
        startAnimation();
    }

    private void startAnimation() {
        new Thread() {
            public void run() {
                while (GameStoreMidlet.this.display.getCurrent() == RefreshCanvas.this) {
                    long now = System.currentTimeMillis();
                    if (now - lastUpdateTime > 400) {
                        dotCount = (dotCount + 1) % 5; // 0-4 titik
                        lastUpdateTime = now;
                        repaint();
                    }
                    try { Thread.sleep(50); } catch (Exception e) {}
                }
            }
        }.start();
    }

    protected void paint(Graphics g) {
        int w = getWidth(), h = getHeight();

        // Background hitam
        g.setColor(0); 
        g.fillRect(0, 0, w, h);

        // Teks "Menyegarkan"
        String baseText = "Menyegarkan";
        int x = w / 2;
        int y = h / 2 - 10;

        g.setColor(0xFFFFFF);
        g.drawString(baseText, x, y, Graphics.HCENTER | Graphics.TOP);

        // Tambahkan titik-titik biru
        g.setColor(0x00AAFF);
        int dotSpacing = Font.getDefaultFont().charWidth('.');
        for (int i = 0; i < dotCount; i++) {
            g.drawChar('.', x + (i * dotSpacing), y + Font.getDefaultFont().getHeight(), Graphics.TOP | Graphics.LEFT);
        }
    }
}

class WelcomeCanvas extends Canvas {
    private int frame = 0;
    private float scrollOffset = 0;
    private int selectedLinkIndex = -1;
    private final int scrollSpeed = 2;

    private String[] lines = {
        "Selamat Datang di Store", "JAVA.REPP.MY.ID!", "", "Ini adalah aplikasi j2me",
        "untuk unduh game dari:", "https://java.repp.my.id", "", "Ada bug? Kunjungi:",
        "https://www.repp.my.id", "Tunggu update game mod", "selanjutnya!", "", "2025 REPP.MY.ID"
    };

    public WelcomeCanvas() {
        new Thread() {
            public void run() {
                while (true) {
                    frame = (frame + 1) % 1000;
                    repaint();
                    try { Thread.sleep(300); } catch (Exception e) {}
                }
            }
        }.start();
    }

    // paint
    protected void paint(Graphics g) {
        int w = getWidth(), h = getHeight();
        g.setColor(0); g.fillRect(0, 0, w, h);

        int boxW = w - 40, boxH = h - 60;
        int x = 20, y = (h - boxH) / 2;
        if (frame < 5) y += (5 - frame) * 10;

        g.setColor(0xFFFFFF);
        g.drawRect(x, y, boxW, boxH);
        g.setColor(0x111111);
        g.fillRect(x + 1, y + 1, boxW - 2, boxH - 2);

        Font f = Font.getFont(Font.FACE_SYSTEM, Font.STYLE_PLAIN, Font.SIZE_SMALL);
        g.setFont(f);
        int fh = f.getHeight();
        int textY = y + 15;
        int contentHeight = lines.length * fh;
        int visibleHeight = boxH - 20;
        int visibleLines = visibleHeight / fh;

        int firstLine = (int)(scrollOffset / fh);
        int offsetY = (int)(scrollOffset % fh);

        for (int i = 0; i <= visibleLines; i++) {
            int lineIndex = firstLine + i;
            if (lineIndex >= lines.length) break;
            String line = lines[lineIndex];
            boolean isLink = line.startsWith("http");
            int tx = x + 10;
            int ty = textY + i * fh - offsetY;

            if (isLink && selectedLinkIndex == lineIndex) {
                g.setColor(0x00AAFF);
                g.fillRect(tx - 2, ty - 1, f.stringWidth(line) + 4, fh);
                g.setColor(0x000000);
            } else if (isLink) {
                g.setColor(0x00AAFF);
            } else {
                g.setColor(0xFFFFFF);
            }

            g.drawString(line, tx, ty, Graphics.TOP | Graphics.LEFT);
        }

        if (contentHeight > visibleHeight) {
            int scrollBarW = 3;
            int barX = x + boxW - scrollBarW - 1;
            int barH = Math.max((boxH * visibleHeight) / contentHeight, 6);
            int scrollableHeight = contentHeight - visibleHeight;
            int barY = y + (int)((boxH - barH) * (scrollOffset / scrollableHeight));
            barY = Math.min(barY, y + boxH - barH);
            g.setColor(0x444444); g.fillRect(barX, y, scrollBarW, boxH);
            g.setColor(0x00AAFF); g.fillRect(barX, barY, scrollBarW, barH);
        }

        g.setColor(0xFFFFFF);
        Font cmdFont = Font.getFont(Font.FACE_SYSTEM, Font.STYLE_PLAIN, Font.SIZE_SMALL);
        g.setFont(cmdFont);
        int cmdBarY = h - cmdFont.getHeight() - 2;
        
        // Gambar "Kunjungi" hanya jika ada link dipilih
        if (selectedLinkIndex != -1) {
            g.drawString("Kunjungi", w / 2, cmdBarY, Graphics.TOP | Graphics.HCENTER);
            g.drawString("0-9:Batal", w - 2, cmdBarY, Graphics.TOP | Graphics.RIGHT);
        } else {
            g.drawString("OK", w / 2, cmdBarY, Graphics.TOP | Graphics.HCENTER);
            g.drawString("<>:Link", w - 2, cmdBarY, Graphics.TOP | Graphics.RIGHT);
        }

        g.setClip(x + 1, y + 1, boxW - 2, boxH - 2);
        for (int i = 0; i <= visibleLines; i++) {
        int lineIndex = firstLine + i;
        if (lineIndex >= lines.length) break;
        String line = lines[lineIndex];
        boolean isLink = line.startsWith("http");
        int tx = x + 10;
        int ty = textY + i * fh - offsetY;
        
        if (isLink && selectedLinkIndex == lineIndex) {
            g.setColor(0x00AAFF);
            g.fillRect(tx - 2, ty - 1, f.stringWidth(line) + 4, fh);
            g.setColor(0x000000);
        } else if (isLink) {
            g.setColor(0x00AAFF);
        } else {
            g.setColor(0xFFFFFF);
        }
        
        g.drawString(line, tx, ty, Graphics.TOP | Graphics.LEFT);
    }   
        g.setClip(0, 0, w, h); // ✅ Kembalikan clip ke seluruh layar
    }

    // keypress
    protected void keyPressed(int key) {
        int action = getGameAction(key);
        Font f = Font.getFont(Font.FACE_SYSTEM, Font.STYLE_PLAIN, Font.SIZE_SMALL);
        int fh = f.getHeight();
        int boxH = getHeight() - 60;
        int visibleHeight = boxH - 20;
        float maxScroll = lines.length * fh - visibleHeight;

        if (action == UP) {
            scrollOffset -= scrollSpeed;
            if (scrollOffset < 0) scrollOffset = 0;
            repaint();
        } else if (action == DOWN) {
            scrollOffset += scrollSpeed;
            if (scrollOffset > maxScroll) scrollOffset = maxScroll;
            repaint();
        } else if (action == LEFT || action == RIGHT) {
            int dir = (action == LEFT) ? -1 : 1;
            int idx = selectedLinkIndex;
            for (int i = 0; i < lines.length; i++) {
                idx = (idx + dir + lines.length) % lines.length;
                if (lines[idx].startsWith("http")) {
                    selectedLinkIndex = idx;
                    ensureSelectedLinkVisible();
                    repaint();
                    break;
                }
            }
        } else if (action == FIRE) {
            if (selectedLinkIndex != -1) {
                try {
                    GameStoreMidlet.this.platformRequest(lines[selectedLinkIndex]);
                } catch (Exception e) {}
            } else {
                lanjutkan(); // 🔥 Tekan OK jika tidak memilih link = Lanjut
                }
            } else if (action == RIGHT) {
                lanjutkan();
            } else {
                selectedLinkIndex = -1;
                repaint();
            }
        }
    private void updateSelectedLinkIndexInView() {
        Font f = Font.getFont(Font.FACE_SYSTEM, Font.STYLE_PLAIN, Font.SIZE_SMALL);
        int fh = f.getHeight();
        int firstVisibleLine = (int)(scrollOffset / fh);
        int visibleLines = (getHeight() - 60 - 20) / fh;
        selectedLinkIndex = findFirstLinkIndexInView(firstVisibleLine, visibleLines);
    }

    private void ensureSelectedLinkVisible() {
        Font f = Font.getFont(Font.FACE_SYSTEM, Font.STYLE_PLAIN, Font.SIZE_SMALL);
        int fh = f.getHeight();
        int boxH = getHeight() - 60;
        int visibleLines = (boxH - 20) / fh;

        int minPixel = (int)(scrollOffset);
        int selPixel = selectedLinkIndex * fh;

        if (selPixel < minPixel) {
            scrollOffset = selPixel;
        } else if (selPixel + fh > minPixel + (boxH - 20)) {
            scrollOffset = selPixel - (boxH - 20 - fh);
        }
    }

    private int findFirstLinkIndexInView(int offset, int maxLines) {
        for (int i = offset; i < offset + maxLines && i < lines.length; i++) {
            if (lines[i].startsWith("http")) return i;
        }
        return -1;
    }
    private void lanjutkan() {
    GameStoreMidlet.this.showGameListFrom(gameListData); // ganti sesuai method milikmu
}
}

class GameListCanvas extends Canvas {
    private String[][] gameData;
    private Image[] icons;
    private int selectedIndex = 0;
    private int scrollOffset = 0;
    private int itemHeight;
    private Font font;

    // Untuk teks berjalan
    private int marqueeOffset = 0;
    private long marqueeLastUpdate = 0;

    public GameListCanvas(String[][] data, Image[] icons) {
        this.gameData = data;
        this.icons = icons;
        font = Font.getFont(Font.FACE_SYSTEM, Font.STYLE_PLAIN, Font.SIZE_SMALL);

        int defaultIconHeight = 48;
        int iconHeight = (icons != null && icons.length > 0 && icons[0] != null) ? icons[0].getHeight() : defaultIconHeight;
        itemHeight = Math.max(font.getHeight(), iconHeight) + 6;

        // addCommand(cmdMenu);
        // addCommand(cmdExit);
        setCommandListener(GameStoreMidlet.this);
    }

    protected void paint(Graphics g) {
    int w = getWidth(), h = getHeight();
    g.setColor(0); g.fillRect(0, 0, w, h);

    int visibleCount = h / itemHeight;
    int start = scrollOffset / itemHeight;

    for (int i = 0; i < visibleCount && (start + i) < gameData.length; i++) {
        int index = start + i;
        int y = i * itemHeight;

        boolean selected = (index == selectedIndex);

        if (selected) {
            g.setColor(0x00AAFF); g.fillRect(0, y, w, itemHeight);
            g.setColor(0x000000);
        } else {
            g.setColor(0xFFFFFF);
        }

        int iconX = 2;
        int iconH = (icons[index] != null) ? icons[index].getHeight() : 48;
        int iconY = y + (itemHeight - iconH) / 2;
        if (iconY < 0) iconY = 0; // cegah negatif


        int textX = iconX + 50; // beri ruang sekitar 48+2px
        int textY = y + (itemHeight - font.getHeight()) / 2;

        System.out.println("Paint index " + index + ": icon = " + (icons[index] != null));

        // Tampilkan ikon
        if (icons != null && icons.length > index && icons[index] != null) {
            g.drawImage(icons[index], iconX, iconY, Graphics.TOP | Graphics.LEFT);

            g.setColor(0xFFFFFF);
            g.drawRect(iconX, iconY, icons[index].getWidth(), icons[index].getHeight());

        }

        String title = gameData[index][0];

        // Jika item dipilih, aktifkan marquee
        if (selected) {
            long now = System.currentTimeMillis();
            if (now - marqueeLastUpdate > 50) { // lebih cepat
                marqueeOffset++;
                marqueeLastUpdate = now;
            }

            int maxTextWidth = w - textX - 4;
            int textWidth = font.stringWidth(title);
            if (textWidth > maxTextWidth) {
                int offset = marqueeOffset % (textWidth + 20);

                // Set clip area ke bagian teks
                g.setClip(textX, y, maxTextWidth, itemHeight);
                g.drawString(title, textX - offset, textY, Graphics.TOP | Graphics.LEFT);
                g.setClip(0, 0, w, h); // reset clip
                repaint();
            } else {
                g.drawString(title, textX, textY, Graphics.TOP | Graphics.LEFT);
            }
        } else {
            g.drawString(title, textX, textY, Graphics.TOP | Graphics.LEFT);
        }
    }

    // Petunjuk tombol di bawah
    g.setColor(0xFFFFFF);
    g.drawString("*:Menu", 2, h - font.getHeight(), Graphics.TOP | Graphics.LEFT);
    g.drawString("#:Keluar", w - 2, h - font.getHeight(), Graphics.TOP | Graphics.RIGHT);
    g.drawString("Pilih", w / 2, h - font.getHeight(), Graphics.TOP | Graphics.HCENTER);
}


    protected void keyPressed(int keyCode) {
        int action = getGameAction(keyCode);
        int h = getHeight();

        if (action == UP || keyCode == -1 || keyCode == KEY_NUM2) {
            if (selectedIndex > 0) selectedIndex--;
            if (selectedIndex * itemHeight < scrollOffset) {
                scrollOffset = selectedIndex * itemHeight;
            }
            marqueeOffset = 0;
            repaint();
        } else if (action == DOWN || keyCode == -2 || keyCode == KEY_NUM8) {
            if (selectedIndex < gameData.length - 1) selectedIndex++;
            int maxVisible = h / itemHeight;
            if (selectedIndex >= (scrollOffset / itemHeight + maxVisible)) {
                scrollOffset = (selectedIndex - maxVisible + 1) * itemHeight;
            }
            marqueeOffset = 0;
            repaint();
        } else if (action == FIRE || keyCode == KEY_NUM5) {
            String[] selectedGame = gameData[selectedIndex];
            GameStoreMidlet.this.showDetailScreen(selectedGame, icons[selectedIndex]);
        } else if (keyCode == KEY_STAR) {
            GameStoreMidlet.this.showPopup("Menu", "1. Segarkan\n2. Tentang Aplikasi");
        } else if (keyCode == KEY_POUND) {
            GameStoreMidlet.this.showPopup("Konfirmasi", "Yakin keluar dari aplikasi?");
        }
    }

    // Untuk update game list saat segarkan
    public void updateGameList(String[][] newData, Image[] newIcons) {
        this.gameData = newData;
        this.icons = newIcons;
        selectedIndex = 0;
        scrollOffset = 0;
        marqueeOffset = 0;
        repaint();
    }
}

class GameDetailCanvas extends Canvas {
    private String[] data;
    private Image cover;
    private Font font;
    private int scrollY = 0, contentHeight;
    private int marqueeOffset = 0;
    private long lastMarqueeTime = 0;
    private int drawLabelAndText(Graphics g, String label, String text, int x, int y, int maxWidth) {
    // Gambar label tebal
    Font bold = Font.getFont(Font.FACE_SYSTEM, Font.STYLE_BOLD, Font.SIZE_SMALL);
    Font normal = Font.getFont(Font.FACE_SYSTEM, Font.STYLE_PLAIN, Font.SIZE_SMALL);

    g.setFont(bold);
    g.drawString(label, x, y, Graphics.TOP | Graphics.LEFT);

    int labelWidth = bold.stringWidth(label);
    g.setFont(normal);
    String[] lines = wrapText(text, maxWidth - labelWidth);
    int yy = y;

    for (int i = 0; i < lines.length; i++) {
        if (i == 0) {
            g.drawString(lines[i], x + labelWidth, yy, Graphics.TOP | Graphics.LEFT);
        } else {
            yy += normal.getHeight();
            g.drawString(lines[i], x, yy, Graphics.TOP | Graphics.LEFT);
        }
    }

    return yy + normal.getHeight();
}


    public GameDetailCanvas(String[] data, Image icon) {
        this.data = data;
        this.font = Font.getFont(Font.FACE_SYSTEM, Font.STYLE_PLAIN, Font.SIZE_SMALL);

        // Coba ambil cover (kolom ke-6)
        try {
            if (data.length >= 6 && data[5] != null && data[5].startsWith("http")) {
                HttpConnection conn = (HttpConnection) Connector.open(data[5]);
                conn.setRequestMethod(HttpConnection.GET);
                InputStream is = conn.openInputStream();

                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                int b;
                while ((b = is.read()) != -1) baos.write(b);
                is.close(); conn.close();

                Image temp = Image.createImage(baos.toByteArray(), 0, baos.size());
                if (temp.getWidth() < getWidth() && temp.getHeight() < getHeight() / 2) {
                    cover = temp;
                }
            }
        } catch (Exception e) {
            cover = null;
        }
    }

protected void paint(Graphics g) {
    int w = getWidth(), h = getHeight();
    g.setColor(0); g.fillRect(0, 0, w, h);
    g.setColor(0xFFFFFF);

    Font normal = Font.getFont(Font.FACE_SYSTEM, Font.STYLE_PLAIN, Font.SIZE_SMALL);
    g.setFont(normal);

    int headerHeight = font.getHeight() + 10;
    int footerHeight = font.getHeight();
    int contentAreaTop = headerHeight;
    int contentAreaHeight = h - headerHeight - footerHeight;

    int y = contentAreaTop - scrollY;

    // === GAMBAR COVER DAN KONTEN DI-SCROLL ===
    if (cover != null && y < h) {
        g.drawImage(cover, w / 2, y, Graphics.TOP | Graphics.HCENTER);
        y += cover.getHeight() + 8;
    }

    y = drawLabelAndText(g, "Deskripsi:", data[4], 5, y, w - 10);
    y += 4;
    y = drawLabelAndText(g, "Layar:", (data.length > 3 ? data[3] : "-"), 5, y, w - 10);
    y += 4;
    String sizeStr = "-";
    if (data.length > 2 && data[2] != null && !data[2].equals("")) {
    try {
        int sizeBytes = Integer.parseInt(data[2]);
        double sizeKB = sizeBytes / 1024.0;
        sizeStr = ((int) sizeKB) + " KB";

        if (sizeKB >= 1024) {
            double sizeMB = sizeKB / 1024.0;
            // Format 1 desimal MB
            sizeStr += " (" + Math.round(sizeMB * 10) / 10.0 + " MB)";
        }
            } catch (Exception e) {
                sizeStr = data[2] + " byte";
            }
        }   
        y = drawLabelAndText(g, "Ukuran:", sizeStr, 5, y, w - 10);
        contentHeight = y + scrollY;

    // === SCROLLBAR ===
    if (contentHeight > contentAreaHeight) {
        int barH = contentAreaHeight * contentAreaHeight / contentHeight;
        int barY = scrollY * contentAreaHeight / contentHeight + headerHeight;
        g.setColor(0x444444); g.fillRect(w - 4, headerHeight, 3, contentAreaHeight);
        g.setColor(0x00AAFF); g.fillRect(w - 4, barY, 3, barH);
    }

    // === HEADER DI ATAS SEGALANYA ===
    g.setColor(0); g.fillRect(0, 0, w, headerHeight); // tutupi apapun di bawah
    g.setColor(0xFFFFFF);
    String title = data[0];
    long now = System.currentTimeMillis();
    if (now - lastMarqueeTime > 100) {
        marqueeOffset++;
        lastMarqueeTime = now;
    }

    int textW = font.stringWidth(title);
    int maxW = w - 10;
    if (textW > maxW) {
        int offset = marqueeOffset % (textW + 20);
        g.setClip(5, 5, maxW, font.getHeight());
        g.drawString(title, 5 - offset, 5, Graphics.TOP | Graphics.LEFT);
        g.setClip(0, 0, w, h);
    } else {
        g.drawString(title, 5, 5, Graphics.TOP | Graphics.LEFT);
    }

    g.drawLine(5, headerHeight - 1, w - 5, headerHeight - 1);

    // === FOOTER (SOFTKEYS) ===
    g.setColor(0); g.fillRect(0, h - footerHeight, w, footerHeight);
    g.setColor(0xFFFFFF);
    g.drawString("*:Unduh", 2, h, Graphics.BOTTOM | Graphics.LEFT);
    g.drawString("#:Kembali", w - 2, h, Graphics.BOTTOM | Graphics.RIGHT);

    repaint();
}

private int drawMultilineText(Graphics g, String text, int x, int y, int maxWidth) {
    int lineHeight = font.getHeight();
    String[] lines = wrapText(text, maxWidth);

    for (int i = 0; i < lines.length; i++) {
        g.drawString(lines[i], x, y, Graphics.TOP | Graphics.LEFT);
        y += lineHeight;
    }

    return y;
}

private String[] wrapText(String text, int maxWidth) {
    Vector lines = new Vector();
    String[] words = splitWordsSafe(text, maxWidth);
    String line = "";

    for (int i = 0; i < words.length; i++) {
        String testLine = line.equals("") ? words[i] : line + " " + words[i];
        int testWidth = font.stringWidth(testLine);

        if (testWidth > maxWidth) {
            if (!line.equals("")) {
                lines.addElement(line);
            }
            line = words[i];
        } else {
            line = testLine;
        }
    }

    if (!line.equals("")) {
        lines.addElement(line);
    }

    String[] result = new String[lines.size()];
    for (int i = 0; i < lines.size(); i++) {
        result[i] = (String) lines.elementAt(i);
    }

    return result;
}

private String[] splitWordsSafe(String text, int maxWidth) {
    Vector result = new Vector();
    int len = text.length();
    int start = 0;

    for (int i = 0; i < len; i++) {
        if (text.charAt(i) == ' ') {
            if (i > start) {
                addWord(result, text.substring(start, i), maxWidth);
            }
            start = i + 1;
        }
    }

    if (start < len) {
        addWord(result, text.substring(start), maxWidth);
    }

    String[] arr = new String[result.size()];
    for (int i = 0; i < result.size(); i++) {
        arr[i] = (String) result.elementAt(i);
    }
    return arr;
}

private void addWord(Vector result, String word, int maxWidth) {
    if (font.stringWidth(word) <= maxWidth) {
        result.addElement(word);
        return;
    }

    String part = "";
    for (int i = 0; i < word.length(); i++) {
        part += word.charAt(i);
        if (font.stringWidth(part) > maxWidth) {
            result.addElement(part.substring(0, part.length() - 1));
            part = "" + word.charAt(i);
        }
    }

    if (!part.equals("")) {
        result.addElement(part);
    }
}

protected void keyPressed(int keyCode) {
    int action = getGameAction(keyCode);
    int scrollAmount = font.getHeight() + 2;

    if (action == UP) {
        scrollY -= scrollAmount;
        if (scrollY < 0) scrollY = 0;
        repaint();
    } else if (action == DOWN) {
        scrollY += scrollAmount;
        if (scrollY > contentHeight - (getHeight() - font.getHeight() * 2)) {
            scrollY = contentHeight - (getHeight() - font.getHeight() * 2);
        }
        repaint();
    } else if (keyCode == KEY_STAR) {
       String gameUrl = data[1];
       String title = data[0];
       String fileName = title.replace(' ', '_') + ".jar";
       
       GameStoreMidlet.this.pendingDownloadURL = gameUrl;
       GameStoreMidlet.this.pendingDownloadTitle = title;
       GameStoreMidlet.this.pendingDownloadFileName = fileName;
       
       GameStoreMidlet.this.lastDetailCanvas = this;
       GameStoreMidlet.this.display.setCurrent(new DirectorySelectionCanvas(GameStoreMidlet.this));

    } else if (keyCode == KEY_POUND) {
        GameStoreMidlet.this.showGameListFrom(GameStoreMidlet.this.gameListData, GameStoreMidlet.this.gameIcons);
    }
}   
}

class DownloadCanvas extends Canvas implements Runnable {
    private String url;
    private String savePath;
    private String gameTitle;
    private GameStoreMidlet midlet;
    private int progressBytes = 0;
    private int totalBytes = -1;
    private boolean isDownloading = false;
    private boolean cancelDownload = false;
    private long lastMarqueeTime = 0;
    private int marqueeOffset = 0;
    private Font font = Font.getDefaultFont();
    private boolean isError = false;
    private boolean isCancelled = false;
    private boolean downloadComplete = false;
    private boolean downloadSuccess = false;
    private boolean downloadFailed = false;

    public DownloadCanvas(GameStoreMidlet midlet, String url, String savePath, String gameTitle) {
        this.midlet = midlet;
        this.url = url;
        this.savePath = savePath;
        this.gameTitle = gameTitle;
        startDownload();
    }

    private void startDownload() {
        isDownloading = true;
        cancelDownload = false;
        progressBytes = 0;
        startAnimation();
        new Thread(this).start();
    }

    private void startAnimation() {
        new Thread() {
            public void run() {
                while (isDownloading) {
                    repaint();
                    try { Thread.sleep(100); } catch (Exception e) {}
                }
                repaint();
            }
        }.start();
    }

    public void run() {
        HttpConnection conn = null;
        InputStream is = null;
        FileConnection fc = null;
        OutputStream os = null;

        try {
            conn = (HttpConnection) Connector.open(url);
            conn.setRequestMethod(HttpConnection.GET);
            totalBytes = (int) conn.getLength();

            is = conn.openInputStream();
            fc = (FileConnection) Connector.open(savePath, Connector.WRITE);
            if (!fc.exists()) fc.create();
            os = fc.openOutputStream();

            byte[] buffer = new byte[1024];
            int len;
            while ((len = is.read(buffer)) != -1) {
                if (cancelDownload) break;
                os.write(buffer, 0, len);
                progressBytes += len;
                repaint();
            }

            os.flush();

            // Jika tidak ada Content-Length dari server, pakai progressBytes sebagai total
            if (totalBytes <= 0) totalBytes = progressBytes;

            isDownloading = false;
            downloadComplete = true;

            if (!cancelDownload) {
                downloadSuccess = true;
            } else {
                isCancelled = true;
                downloadFailed = true;
            }

        } catch (Exception e) {
            isDownloading = false;
            downloadComplete = true;
            downloadSuccess = false;
            downloadFailed = true;
            isError = true;
        } finally {
            try { if (os != null) os.close(); } catch (Exception e) {}
            try { if (is != null) is.close(); } catch (Exception e) {}
            try { if (conn != null) conn.close(); } catch (Exception e) {}
            try { if (fc != null) fc.close(); } catch (Exception e) {}
            repaint();
        }
    }

    protected void paint(Graphics g) {
        int w = getWidth(), h = getHeight();
        g.setColor(0); g.fillRect(0, 0, w, h);
        g.setFont(font);

        // Header
        g.setColor(0x000000);
        g.fillRect(0, 0, w, font.getHeight() + 6);
        g.setColor(0xFFFFFF);

        long now = System.currentTimeMillis();
        if (now - lastMarqueeTime > 100) {
            marqueeOffset++;
            lastMarqueeTime = now;
        }

        int textW = font.stringWidth(gameTitle);
        int maxW = w - 10;
        if (textW > maxW) {
            int offset = marqueeOffset % (textW + 20);
            g.setClip(5, 3, maxW, font.getHeight());
            g.drawString(gameTitle, 5 - offset, 3, Graphics.TOP | Graphics.LEFT);
            g.setClip(0, 0, w, h);
        } else {
            g.drawString(gameTitle, 5, 3, Graphics.TOP | Graphics.LEFT);
        }

        // Status
        String status = "Mengunduh...";
        int statusColor = 0x00FF00;
        if (isError || isCancelled) {
            status = "Dibatalkan!";
            statusColor = 0xFF0000;
        } else if (downloadComplete && downloadSuccess) {
            status = "Berhasil!";
            statusColor = 0x00FF00;
        }

        g.setColor(statusColor);
        g.drawString(status, w / 2, font.getHeight() + 10, Graphics.TOP | Graphics.HCENTER);

        // Progress bar
        int barW = w - 40;
        int barH = 10;
        int barX = 20;
        int barY = font.getHeight() + 30;

        g.setColor((isError || isCancelled) ? 0xFF0000 : 0xFFFFFF);
        g.drawRect(barX, barY, barW, barH);

        int fillW = 0;
        int percent = 0;
        if (totalBytes > 0) {
            fillW = (progressBytes * barW) / totalBytes;
            percent = (progressBytes * 100) / totalBytes;
        }
        if (fillW < 1 && progressBytes > 0) fillW = 1;
        if (fillW > barW) fillW = barW;
        if (percent > 100) percent = 100;

        g.setColor((isError || isCancelled) ? 0x880000 : 0x00FF00);
        g.fillRect(barX + 1, barY + 1, fillW - 2, barH - 2);

        // Progress text
        int kb = progressBytes / 1024;
        String progText = kb + "KB / " + percent + "%";
        g.setColor(0xFFFFFF);
        g.drawString(progText, w / 2, barY + barH + 2, Graphics.TOP | Graphics.HCENTER);

        // Footer
        int footerY = h - font.getHeight() - 2;
        g.setColor(0); g.fillRect(0, footerY, w, font.getHeight() + 4);
        g.setColor(0xFFFFFF);

        if (isDownloading) {
            g.drawString("#:Batal", w - 2, h, Graphics.BOTTOM | Graphics.RIGHT);
        } else if (isCancelled || isError) {
            g.drawString("*:Coba Lagi", 2, h, Graphics.BOTTOM | Graphics.LEFT);
            g.drawString("#:Kembali", w - 2, h, Graphics.BOTTOM | Graphics.RIGHT);
        } else {
            g.drawString("#:Kembali", w - 2, h, Graphics.BOTTOM | Graphics.RIGHT);
        }
    }

    protected void keyPressed(int keyCode) {
        if (isDownloading) {
            if (keyCode == KEY_POUND) {
                cancelDownload = true;
                isDownloading = false;
                isCancelled = true;
            }
        } else if (isError || isCancelled) {
            if (keyCode == KEY_STAR) {
                midlet.display.setCurrent(new DownloadCanvas(midlet, url, savePath, gameTitle));
            } else if (keyCode == KEY_POUND) {
                midlet.display.setCurrent(midlet.lastDetailCanvas);
            }
        } else {
            if (keyCode == KEY_POUND) {
                midlet.display.setCurrent(midlet.lastDetailCanvas);
            }
        }
    }
}

public class DirectorySelectionCanvas extends Canvas {
    private GameStoreMidlet midlet;
    private String currentPath = "file:///";
    private String[] folders;
    private int selectedIndex = 0;
    private int scrollOffset = 0;
    private int maxVisible = 0;


    public DirectorySelectionCanvas(GameStoreMidlet midlet) {
        this.midlet = midlet;
        loadFolders();
    }

    private void loadFolders() {
        try {
            java.util.Vector list = new java.util.Vector();
            list.addElement(".."); // untuk kembali
            
            if (currentPath.equals("file:///")) { // List root seperti root1/, root2/, SDCard/
                java.util.Enumeration roots = FileSystemRegistry.listRoots();
                while (roots.hasMoreElements()) {
                    String root = (String) roots.nextElement();
                    list.addElement(root); // sudh pakai trailing slash, misal "root1/"
                    }
                } else {
                    FileConnection dir = (FileConnection) Connector.open(currentPath, Connector.READ);
                    java.util.Enumeration e = dir.list("*", true);
                    while (e.hasMoreElements()) {
                        String name = (String) e.nextElement();
                        if (name.endsWith("/")) list.addElement(name);
                    }
                    dir.close();
                }
                
                folders = new String[list.size()];
                list.copyInto(folders);
            } catch (Exception e) {
                folders = new String[] { ".." };
            }
        }

   protected void paint(Graphics g) {
    int w = getWidth(), h = getHeight();
    Font font = Font.getFont(Font.FACE_SYSTEM, Font.STYLE_PLAIN, Font.SIZE_SMALL);
    int footerHeight = font.getHeight() + 4;
    int listTop = 20;
    int listHeight = h - listTop - footerHeight;

    g.setColor(0); g.fillRect(0, 0, w, h);
    g.setColor(0xFFFFFF);
    g.setFont(font);

    g.drawString("Folder: " + currentPath, 5, 2, Graphics.TOP | Graphics.LEFT);

    maxVisible = listHeight / font.getHeight();

    for (int i = 0; i < maxVisible && (i + scrollOffset) < folders.length; i++) {
        int y = listTop + i * font.getHeight();
        int idx = i + scrollOffset;
        if (idx == selectedIndex) {
            g.setColor(0x00AAFF);
            g.fillRect(0, y, w, font.getHeight());
            g.setColor(0x000000);
        } else {
            g.setColor(0xFFFFFF);
        }
        g.drawString(folders[idx], 5, y, Graphics.TOP | Graphics.LEFT);
    }

    // Footer background
    g.setColor(0x000000);
    g.fillRect(0, h - footerHeight, w, footerHeight);

    // Footer text
    g.setColor(0x888888);
    String foot = "*:Simpan   OK:Buka   #:Kembali";
    if (currentPath.equals("file:///")) foot = "*:Simpan   OK:Buka   #:Batal";
    g.drawString(foot, w / 2, h - 2, Graphics.BOTTOM | Graphics.HCENTER);
}

    protected void keyPressed(int keyCode) {
        int fontHeight = Font.getFont(Font.FACE_SYSTEM, Font.STYLE_PLAIN, Font.SIZE_SMALL).getHeight();
        int action = getGameAction(keyCode);

        if (action == UP) {
            if (selectedIndex > 0) selectedIndex--;
            if (selectedIndex < scrollOffset) scrollOffset--;
            repaint();
        } else if (action == DOWN) {
            if (selectedIndex < folders.length - 1) selectedIndex++;
            if (selectedIndex >= scrollOffset + maxVisible) scrollOffset++;
            repaint();
        } else if (action == FIRE || keyCode == KEY_NUM5) {
            String sel = folders[selectedIndex];
            if (sel.equals("..")) {
                if (!currentPath.equals("file:///")) {
                    int lastSlash = currentPath.lastIndexOf('/', currentPath.length() - 2);
                    if (lastSlash > 7) {
                        currentPath = currentPath.substring(0, lastSlash + 1);
                    } else {
                        currentPath = "file:///";
                    }
                    loadFolders(); selectedIndex = 0;
                    repaint();
                }
            } else {
                currentPath = currentPath + sel;
                loadFolders(); selectedIndex = 0;
                repaint();
            }
        } else if (keyCode == KEY_STAR) {
            // Konfirmasi pilih direktori simpan
            String path = currentPath + midlet.pendingDownloadFileName;
            midlet.display.setCurrent(new DownloadCanvas(midlet, midlet.pendingDownloadURL, path, midlet.pendingDownloadTitle));
        } else if (keyCode == KEY_POUND) {
            if (currentPath.equals("file:///")) {
                midlet.backToDetail(); // batal
            } else {
                // sama seperti memilih ".."
                keyPressed(KEY_NUM5);
            }
        }
    }
}

}