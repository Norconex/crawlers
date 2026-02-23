package com.norconex.crawler.core.cluster.impl.hazelcast;

class JdbiExampleTest {
    //    private DataSource dataSource;
    //    private Jdbi jdbi;
    //
    //    @BeforeEach
    //    void setUp() {
    //        HikariConfig config = new HikariConfig();
    //        config.setJdbcUrl("jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1");
    //        config.setUsername("sa");
    //        config.setPassword("");
    //        dataSource = new HikariDataSource(config);
    //        jdbi = Jdbi.create(dataSource);
    //        jdbi.useHandle(handle -> {
    //            handle.execute("CREATE TABLE person (id INT PRIMARY KEY, name VARCHAR(100))");
    //            handle.execute("INSERT INTO person (id, name) VALUES (?, ?)", 1, "Alice");
    //            handle.execute("INSERT INTO person (id, name) VALUES (?, ?)", 2, "Bob");
    //        });
    //    }
    //
    //    @AfterEach
    //    void tearDown() {
    //        ((HikariDataSource) dataSource).close();
    //    }
    //
    //    public static class Person {
    //        private int id;
    //        private String name;
    //        public int getId() { return id; }
    //        public void setId(int id) { this.id = id; }
    //        public String getName() { return name; }
    //        public void setName(String name) { this.name = name; }
    //    }
    //
    //    @Test
    //    void testJdbiSelectToPojo() {
    //        List<Person> people = jdbi.withHandle(handle ->
    //            handle.createQuery("SELECT id, name FROM person ORDER BY id")
    //                  .map(BeanMapper.factory(Person.class))
    //                  .list()
    //        );
    //        assertThat(people).hasSize(2);
    //        assertThat(people.get(0).getName()).isEqualTo("Alice");
    //        assertThat(people.get(1).getName()).isEqualTo("Bob");
    //    }
    //
    //    public static class PersonExt {
    //        private int id;
    //        private String name;
    //        private Map<String, Object> extra = new HashMap<>();
    //        public int getId() { return id; }
    //        public void setId(int id) { this.id = id; }
    //        public String getName() { return name; }
    //        public void setName(String name) { this.name = name; }
    //        public Map<String, Object> getExtra() { return extra; }
    //        public void setExtra(Map<String, Object> extra) { this.extra = extra; }
    //    }
    //
    //    @Test
    //    void testJdbiBindMainFieldsAndStoreExtraAsClob() throws Exception {
    //        ObjectMapper mapper = new ObjectMapper();
    //        jdbi.useHandle(handle -> {
    //            handle.execute("CREATE TABLE person_ext (id INT PRIMARY KEY, name VARCHAR(100), extra_data CLOB)");
    //        });
    //        PersonExt p = new PersonExt();
    //        p.setId(10);
    //        p.setName("Eve");
    //        p.getExtra().put("city", "Montreal");
    //        p.getExtra().put("age", 42);
    //        String extraJson = mapper.writeValueAsString(p.getExtra());
    //        jdbi.useHandle(handle -> {
    //            handle.createUpdate("INSERT INTO person_ext (id, name, extra_data) VALUES (:id, :name, :extra_data)")
    //                .bind("id", p.getId())
    //                .bind("name", p.getName())
    //                .bind("extra_data", extraJson)
    //                .execute();
    //        });
    //        PersonExt loaded = jdbi.withHandle(handle ->
    //            handle.createQuery("SELECT id, name, extra_data FROM person_ext WHERE id = :id")
    //                  .bind("id", 10)
    //                  .map((rs, ctx) -> {
    //                      PersonExt pe = new PersonExt();
    //                      pe.setId(rs.getInt("id"));
    //                      pe.setName(rs.getString("name"));
    //                      String json = rs.getString("extra_data");
    //                      try {
    //                          pe.setExtra(mapper.readValue(json, Map.class));
    //                      } catch (Exception e) {
    //                          throw new UnableToExecuteStatementException(e);
    //                      }
    //                      return pe;
    //                  })
    //                  .one()
    //        );
    //        assertThat(loaded.getId()).isEqualTo(10);
    //        assertThat(loaded.getName()).isEqualTo("Eve");
    //        assertThat(loaded.getExtra()).containsEntry("city", "Montreal").containsEntry("age", 42);
    //    }
}
