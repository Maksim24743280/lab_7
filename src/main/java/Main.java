import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.util.List;

public class Main {

    // URL для запуска H2 в оперативной памяти (testdb).
    // Параметр DB_CLOSE_DELAY=-1 не позволяет базе очиститься из памяти до завершения программы.
    private static final String DB_URL = "jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1";
    // Стандартный логин администратора H2
    private static final String DB_USER = "sa";
    // Стандартный пустой пароль H2
    private static final String DB_PASSWORD = "";

    public static void main(String[] args) {
        // Открываем соединение с БД через try-with-resources,
        // чтобы оно гарантированно закрылось при выходе из блока.
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {
            System.out.println("Успешное подключение к БД H2.\n");

            // =========================================================================
            // ПОДГОТОВКА: Создание схемы study и выполнение скрипта music-create.sql
            // =========================================================================
            prepareMusicDatabase(conn);

            // =========================================================================
            // ЗАДАНИЕ № 1: Получить и вывести все записи из таблицы study.music
            // =========================================================================
            System.out.println("=== Задание 1: Все музыкальные композиции ===");
            printAllMusic(conn);

            // =========================================================================
            // ЗАДАНИЕ № 2: Композиции без букв 'm' и 't' без учёта регистра
            // =========================================================================
            System.out.println("\n=== Задание 2: Композиции без букв 'm' и 't' (без учёта регистра) ===");
            printFilteredMusic(conn);

            // =========================================================================
            // ЗАДАНИЕ № 3: Добавление своей любимой композиции с помощью INSERT
            // =========================================================================
            System.out.println("\n=== Задание 3: Добавление любимой композиции ===");
            addMusicTrack(conn, "Numb");
            // Выводим обновлённый список композиций для проверки
            printAllMusic(conn);

            // =========================================================================
            // ЗАДАНИЕ № 4: Создание таблиц и загрузка уникальных данных из books.json
            // =========================================================================
            System.out.println("\n=== Задание 4: Загрузка уникальных данных из books.json ===");
            createLibraryTables(conn);
            importJsonToDatabase(conn, "books.json");

            // =========================================================================
            // ЗАДАНИЕ № 5: Список книг, отсортированный по году издания (ORDER BY)
            // =========================================================================
            System.out.println("\n=== Задание 5: Книги, отсортированные по году издания ===");
            printBooksSortedByYear(conn);

            // =========================================================================
            // ЗАДАНИЕ № 6: Книги, изданные до 2000 года (WHERE publishing_year < 2000)
            // =========================================================================
            System.out.println("\n=== Задание 6: Книги, изданные до 2000 года ===");
            printBooksBefore2000(conn);

            // =========================================================================
            // ЗАДАНИЕ № 7: Добавление информации о себе и своих любимых книг
            // =========================================================================
            System.out.println("\n=== Задание 7: Добавление информации о себе и любимых книг ===");
            addMyProfileAndBooks(conn);

            // =========================================================================
            // ЗАДАНИЕ № 8: Удаление таблиц посетителей и книг (DROP TABLE)
            // =========================================================================
            System.out.println("\n=== Задание 8: Удаление таблиц посетителей и книг ===");
            dropLibraryTables(conn);

        } catch (SQLException e) {
            // Перехватываем ошибки выполнения SQL-запросов и работы JDBC
            System.err.println("Ошибка при работе с базой данных: " + e.getMessage());
            e.printStackTrace();
        } catch (IOException e) {
            // Перехватываем ошибки чтения файлов music-create.sql или books.json
            System.err.println("Ошибка при чтении файлов: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Подготовка БД: создание схемы study и запуск скрипта music-create.sql.
     */
    private static void prepareMusicDatabase(Connection conn) throws SQLException, IOException {
        // Создаём Statement для выполнения DDL-команд и скрипта инициализации
        try (Statement stmt = conn.createStatement()) {
            // Создаём схему study, если она ещё не создана
            stmt.execute("CREATE SCHEMA IF NOT EXISTS study");

            // Считываем содержимое файла music-create.sql из корня проекта
            String sqlScript = Files.readString(Path.of("music-create.sql"));

            // Разделяем скрипт на отдельные SQL-команды по символу ';'
            for (String query : sqlScript.split(";")) {
                // Убираем пробелы и переносы строк по краям каждой команды
                String trimmed = query.trim();
                // Выполняем непустую команду в базе данных
                if (!trimmed.isEmpty()) {
                    stmt.execute(trimmed);
                }
            }
            System.out.println("Схема study создана, скрипт music-create.sql выполнен.");
        }
    }

    /**
     * Задание 1: Получить и вывести все записи из таблицы study.music.
     */
    private static void printAllMusic(Connection conn) throws SQLException {
        // SQL-запрос для получения всех записей из таблицы study.music
        String sql = "SELECT id, name FROM study.music ORDER BY id";

        // Открываем Statement и выполняем SELECT через executeQuery(), получая ResultSet
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            // Построчно проходим по результатам выборки
            while (rs.next()) {
                // Извлекаем значение столбца id
                int id = rs.getInt("id");
                // Извлекаем значение столбца name
                String name = rs.getString("name");
                // Печатаем строку в консоль
                System.out.printf("%2d | %s%n", id, name);
            }
        }
    }

    /**
     * Задание 2: Вывести композиции, в названиях которых отсутствуют обе буквы 'm' и 't' (без учёта регистра).
     */
    private static void printFilteredMusic(Connection conn) throws SQLException {
        // LOWER(name) переводит название в нижний регистр, чтобы фильтрация не зависела от регистра букв.
        // NOT LIKE '%m%' AND NOT LIKE '%t%' исключает названия, содержащие хотя бы одну из этих букв.
        String sql = """
                SELECT id, name
                FROM study.music
                WHERE LOWER(name) NOT LIKE '%m%'
                  AND LOWER(name) NOT LIKE '%t%'
                ORDER BY id
                """;

        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                System.out.printf("%2d | %s%n", rs.getInt("id"), rs.getString("name"));
            }
        }
    }

    /**
     * Задание 3: Добавить любую свою любимую композицию с помощью INSERT.
     */
    private static void addMusicTrack(Connection conn, String trackName) throws SQLException {
        // В music-create.sql столбец id не имеет AUTO_INCREMENT,
        // поэтому вычисляем следующий свободный id через COALESCE(MAX(id), 0) + 1.
        String sql = """
                INSERT INTO study.music (id, name)
                VALUES ((SELECT COALESCE(MAX(id), 0) + 1 FROM study.music), ?)
                """;

        // Подготавливаем параметризованный запрос
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            // Подставляем название композиции на место первого '?'
            ps.setString(1, trackName);
            // Выполняем INSERT и получаем количество добавленных строк
            int inserted = ps.executeUpdate();
            System.out.println("Добавлено строк: " + inserted + " -> " + trackName);
        }
    }

    /**
     * Задание 4 (часть 1): Создание таблиц для посетителей, книг и связи между ними.
     */
    private static void createLibraryTables(Connection conn) throws SQLException {
        // В books.json встречаются разные посетители с одинаковым номером телефона,
        // поэтому уникальность посетителя задаём составным ограничением UNIQUE (name, surname, phone).
        String createVisitorsSql = """
                CREATE TABLE IF NOT EXISTS visitors (
                    id INT AUTO_INCREMENT PRIMARY KEY,
                    name VARCHAR(100) NOT NULL,
                    surname VARCHAR(100) NOT NULL,
                    phone VARCHAR(30) NOT NULL,
                    subscribed BOOLEAN NOT NULL,
                    CONSTRAINT uq_visitor UNIQUE (name, surname, phone)
                )
                """;

        // Уникальность каждой книги обеспечивается по её коду ISBN (isbn UNIQUE).
        String createBooksSql = """
                CREATE TABLE IF NOT EXISTS books (
                    id INT AUTO_INCREMENT PRIMARY KEY,
                    name VARCHAR(255) NOT NULL,
                    author VARCHAR(255) NOT NULL,
                    publishing_year INT NOT NULL,
                    isbn VARCHAR(50) NOT NULL UNIQUE,
                    publisher VARCHAR(255) NOT NULL
                )
                """;

        // Связующая таблица "многие ко многим" между посетителями и книгами.
        // Составной PRIMARY KEY (visitor_id, book_id) не допускает дублирования связей.
        String createVisitorBooksSql = """
                CREATE TABLE IF NOT EXISTS visitor_books (
                    visitor_id INT NOT NULL,
                    book_id INT NOT NULL,
                    PRIMARY KEY (visitor_id, book_id),
                    FOREIGN KEY (visitor_id) REFERENCES visitors(id) ON DELETE CASCADE,
                    FOREIGN KEY (book_id) REFERENCES books(id) ON DELETE CASCADE
                )
                """;

        try (Statement stmt = conn.createStatement()) {
            // Создаём все три таблицы
            stmt.execute(createVisitorsSql);
            stmt.execute(createBooksSql);
            stmt.execute(createVisitorBooksSql);
            System.out.println("Таблицы visitors, books и visitor_books успешно созданы.");
        }
    }

    /**
     * Задание 4 (часть 2): Чтение файла books.json и загрузка только уникальных записей в БД.
     */
    private static void importJsonToDatabase(Connection conn, String filePath) throws IOException, SQLException {
        // Создаём Jackson ObjectMapper для чтения JSON
        ObjectMapper mapper = new ObjectMapper();
        // Десериализуем JSON-массив в список объектов VisitorDto
        List<VisitorDto> visitors = mapper.readValue(
                new File(filePath),
                new TypeReference<List<VisitorDto>>() {}
        );

        // Команда MERGE INTO ... KEY(...) в H2 добавляет запись, если её ещё нет по ключу,
        // или обновляет существующую — это исключает появление дубликатов.
        String mergeVisitorSql = """
                MERGE INTO visitors (name, surname, phone, subscribed)
                KEY (name, surname, phone)
                VALUES (?, ?, ?, ?)
                """;

        String mergeBookSql = """
                MERGE INTO books (name, author, publishing_year, isbn, publisher)
                KEY (isbn)
                VALUES (?, ?, ?, ?, ?)
                """;

        // Запросы для поиска ID вставленных/существующих записей
        String findVisitorIdSql = "SELECT id FROM visitors WHERE name = ? AND surname = ? AND phone = ?";
        String findBookIdSql = "SELECT id FROM books WHERE isbn = ?";

        // Запрос для создания связи между посетителем и книгой без дублей
        String mergeLinkSql = """
                MERGE INTO visitor_books (visitor_id, book_id)
                KEY (visitor_id, book_id)
                VALUES (?, ?)
                """;

        try (PreparedStatement psVisitor = conn.prepareStatement(mergeVisitorSql);
             PreparedStatement psBook = conn.prepareStatement(mergeBookSql);
             PreparedStatement psFindVisitor = conn.prepareStatement(findVisitorIdSql);
             PreparedStatement psFindBook = conn.prepareStatement(findBookIdSql);
             PreparedStatement psLink = conn.prepareStatement(mergeLinkSql)) {

            // Проходим по всем посетителям из JSON
            for (VisitorDto v : visitors) {
                // Сохраняем уникального посетителя
                psVisitor.setString(1, v.name());
                psVisitor.setString(2, v.surname());
                psVisitor.setString(3, v.phone());
                psVisitor.setBoolean(4, v.subscribed());
                psVisitor.executeUpdate();

                // Получаем ID посетителя из таблицы visitors
                int visitorId = getVisitorId(psFindVisitor, v.name(), v.surname(), v.phone());

                // Проходим по списку любимых книг посетителя
                if (v.favoriteBooks() != null) {
                    for (BookDto b : v.favoriteBooks()) {
                        // Сохраняем уникальную книгу по ISBN
                        psBook.setString(1, b.name());
                        psBook.setString(2, b.author());
                        psBook.setInt(3, b.publishingYear());
                        psBook.setString(4, b.isbn());
                        psBook.setString(5, b.publisher());
                        psBook.executeUpdate();

                        // Получаем ID книги из таблицы books
                        int bookId = getBookId(psFindBook, b.isbn());

                        // Связываем посетителя и книгу
                        psLink.setInt(1, visitorId);
                        psLink.setInt(2, bookId);
                        psLink.executeUpdate();
                    }
                }
            }
        }

        // Выводим количество уникальных записей в таблицах для самопроверки
        try (Statement stmt = conn.createStatement()) {
            ResultSet rsV = stmt.executeQuery("SELECT COUNT(*) FROM visitors");
            if (rsV.next()) System.out.println("Загружено уникальных посетителей: " + rsV.getInt(1));

            ResultSet rsB = stmt.executeQuery("SELECT COUNT(*) FROM books");
            if (rsB.next()) System.out.println("Загружено уникальных книг: " + rsB.getInt(1));
        }
    }

    /**
     * Вспомогательный метод для получения ID посетителя по (name, surname, phone).
     */
    private static int getVisitorId(PreparedStatement ps, String name, String surname, String phone) throws SQLException {
        ps.setString(1, name);
        ps.setString(2, surname);
        ps.setString(3, phone);
        try (ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                return rs.getInt("id");
            }
        }
        throw new SQLException("Посетитель не найден: " + name + " " + surname);
    }

    /**
     * Вспомогательный метод для получения ID книги по ISBN.
     */
    private static int getBookId(PreparedStatement ps, String isbn) throws SQLException {
        ps.setString(1, isbn);
        try (ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                return rs.getInt("id");
            }
        }
        throw new SQLException("Книга не найдена по ISBN: " + isbn);
    }

    /**
     * Задание 5: Получить и вывести список книг, отсортированный по году издания (ORDER BY).
     */
    private static void printBooksSortedByYear(Connection conn) throws SQLException {
        // Сортировка выполняется средствами SQL через ORDER BY publishing_year ASC
        String sql = "SELECT publishing_year, name, author, isbn FROM books ORDER BY publishing_year ASC";

        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                System.out.printf("%d | \"%s\" — %s (ISBN: %s)%n",
                        rs.getInt("publishing_year"),
                        rs.getString("name"),
                        rs.getString("author"),
                        rs.getString("isbn"));
            }
        }
    }

    /**
     * Задание 6: Получить и вывести книги, изданные до 2000 года (WHERE publishing_year < 2000).
     */
    private static void printBooksBefore2000(Connection conn) throws SQLException {
        // Фильтрация по году издания меньше 2000 на уровне SQL-запроса
        String sql = """
                SELECT publishing_year, name, author
                FROM books
                WHERE publishing_year < 2000
                ORDER BY publishing_year ASC
                """;

        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                System.out.printf("%d | \"%s\" — %s%n",
                        rs.getInt("publishing_year"),
                        rs.getString("name"),
                        rs.getString("author"));
            }
        }
    }

    /**
     * Задание 7: Добавить информацию о себе и свои любимые книги, затем прочитать и вывести их из БД.
     */
    private static void addMyProfileAndBooks(Connection conn) throws SQLException {
        String myName = "Maksim";
        String mySurname = "Lutsky";
        String myPhone = "967-167-6767";
        boolean mySubscribed = true;

        // 1. Добавляем себя в таблицу visitors
        String insertVisitorSql = "INSERT INTO visitors (name, surname, phone, subscribed) VALUES (?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(insertVisitorSql)) {
            ps.setString(1, myName);
            ps.setString(2, mySurname);
            ps.setString(3, myPhone);
            ps.setBoolean(4, mySubscribed);
            ps.executeUpdate();
        }

        // 2. Добавляем две свои любимые книги в таблицу books
        String mergeBookSql = """
                MERGE INTO books (name, author, publishing_year, isbn, publisher)
                KEY (isbn)
                VALUES (?, ?, ?, ?, ?)
                """;
        try (PreparedStatement ps = conn.prepareStatement(mergeBookSql)) {
            // Первая любимая книга
            ps.setString(1, "The Master and Margarita");
            ps.setString(2, "Mikhail Bulgakov");
            ps.setInt(3, 1967);
            ps.setString(4, "0134685997");
            ps.setString(5, "АСТ");
            ps.executeUpdate();

            // Вторая любимая книга
            ps.setString(1, "Crime and punishment");
            ps.setString(2, "Fyodor Dostoevsky");
            ps.setInt(3, 1866);
            ps.setString(4, "0132350882");
            ps.setString(5, "Alphabet");
            ps.executeUpdate();
        }

        // 3. Связываем добавленного посетителя и его любимые книги в таблице visitor_books
        String linkBooksSql = """
                INSERT INTO visitor_books (visitor_id, book_id)
                SELECT v.id, b.id
                FROM visitors v, books b
                WHERE v.name = ? AND v.surname = ? AND v.phone = ?
                  AND b.isbn IN (?, ?)
                """;
        try (PreparedStatement ps = conn.prepareStatement(linkBooksSql)) {
            ps.setString(1, myName);
            ps.setString(2, mySurname);
            ps.setString(3, myPhone);
            ps.setString(4, "0134685997");
            ps.setString(5, "0132350882");
            ps.executeUpdate();
        }

        // 4. Читаем добавленные данные из базы с помощью JOIN и выводим в консоль
        String selectMyDataSql = """
                SELECT v.name AS visitor_name, v.surname, v.phone, v.subscribed,
                       b.name AS book_name, b.author, b.publishing_year, b.isbn
                FROM visitors v
                JOIN visitor_books vb ON v.id = vb.visitor_id
                JOIN books b ON vb.book_id = b.id
                WHERE v.name = ? AND v.surname = ? AND v.phone = ?
                """;

        try (PreparedStatement ps = conn.prepareStatement(selectMyDataSql)) {
            ps.setString(1, myName);
            ps.setString(2, mySurname);
            ps.setString(3, myPhone);

            try (ResultSet rs = ps.executeQuery()) {
                System.out.println("Данные о себе и любимых книгах, прочитанные из БД:");
                while (rs.next()) {
                    System.out.printf("Посетитель: %s %s (тел: %s, подписка: %b) -> Книга: \"%s\" (%s, %d г., ISBN: %s)%n",
                            rs.getString("visitor_name"),
                            rs.getString("surname"),
                            rs.getString("phone"),
                            rs.getBoolean("subscribed"),
                            rs.getString("book_name"),
                            rs.getString("author"),
                            rs.getInt("publishing_year"),
                            rs.getString("isbn"));
                }
            }
        }
    }

    /**
     * Задание 8: Удалить созданные таблицы посетителей и книг с помощью DROP TABLE.
     */
    private static void dropLibraryTables(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            // Сначала удаляем дочернюю таблицу связей visitor_books, так как она ссылается на visitors и books
            stmt.execute("DROP TABLE IF EXISTS visitor_books");
            // Затем удаляем таблицы books и visitors
            stmt.execute("DROP TABLE IF EXISTS books");
            stmt.execute("DROP TABLE IF EXISTS visitors");
            System.out.println("Таблицы visitor_books, books и visitors успешно удалены.");
        }
    }

    // =========================================================================
    // Java Records (DTO) для разбора структуры файла books.json
    // =========================================================================

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record VisitorDto(
            String name,
            String surname,
            String phone,
            boolean subscribed,
            List<BookDto> favoriteBooks
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record BookDto(
            String name,
            String author,
            int publishingYear,
            String isbn,
            String publisher
    ) {}
}