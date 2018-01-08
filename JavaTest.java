package com.java;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.IntSummaryStatistics;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

public class JavaTest {

    public enum UserTypeCodes {

        ACCOUNT_MANAGER, DEALER_CONTACT, EMPLOYEE, WHOLESALE_DEALER, OTHER;

    }

    public static final String KEYWORD_PATTERN = "\\[[A-Z,_,-]+\\]"; // \[[A-Z,_,-]+\]
    final static Date date = new Date();
    final List<Message> messages = new ArrayList<>();
    final static List<String> m = Collections.EMPTY_LIST;// new ArrayList<>();
    final static List<String> n = new ArrayList<>();

    public static void main(String[] args) {
        final Set<Integer> sets = new HashSet<>();
        sets.add(10);
        sets.add(9);
        sets.add(11);
		  sets.add(12);
        sets.add(null);
        sets.removeAll(null);
        System.out.println(Collections.max(sets));

        // 3 apple, 2 banana, others 1
        final List<Item> items = Arrays.asList(new Item("apple", 10, new BigDecimal("9.99")), new Item("banana", 20, new BigDecimal("19.99")), new Item("orang", 10, new BigDecimal("29.99")),
                new Item("watermelon", 10, new BigDecimal("29.99")), new Item("papaya", 20, new BigDecimal("9.99")), new Item("apple", 10, new BigDecimal("19.99")),
                new Item("banana", 10, new BigDecimal("19.99")), new Item("apple", 20, new BigDecimal("9.99")));

        // group by price
        final Map<String, IntSummaryStatistics> groupByNameMap = items.stream().collect(Collectors.groupingBy(Item::getName, Collectors.summarizingInt(Item::getQty)));

        System.out.println(groupByNameMap);
        System.out.println();
        final Map<String, Set<BigDecimal>> itemSet = items.stream().collect(Collectors.groupingBy(Item::getName, Collectors.mapping(Item::getPrice, Collectors.toSet())));
        System.out.println(itemSet);

        // group by price, uses 'mapping' to convert List<Item> to Set<String>
        // final Map<BigDecimal, Set<String>> result = items.stream().collect(Collectors.groupingBy(Item::getPrice, Collectors.mapping(Item::getName, Collectors.toSet())));

        // System.out.println(result);

    }

    public static void main2(String[] args) {

        final String query = "insert into CONNECTION_COMMENTS_TEST (select * from comments@skelet where GSS_id is not null and created_date < sysdate-%s and created_date >= sysdate-%s);";
        final StringBuilder stb = new StringBuilder();
        for (int i = 10; i <= 360; i = i + 10) {
            stb.append("--between days range past " + (i - 10) + " and " + i);
            stb.append(System.lineSeparator());
            stb.append(prepareStringFormat(query, String.valueOf(i - 10), String.valueOf(i)));
            stb.append(System.lineSeparator());
        }

        System.out.println(stb);
    }

    public static String getSaltutionForUserType(String userTypeCode) {
        return UserTypeCodes.DEALER_CONTACT == UserTypeCodes.valueOf(userTypeCode) ? "Business Partner" : "collega";
    }

    private static String prepareStringFormat(String query, String beginDateRange, String endDateRange) {
        final String mailContent = String.format(query, beginDateRange, endDateRange);
        return mailContent;

    }

    public static Date getStartOfDate(Date date) {
        final Calendar cal = Calendar.getInstance();
        cal.setTime(date);
        cal.set(Calendar.HOUR_OF_DAY, 12);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal.getTime();
    }

    public File mainNethod() throws IOException {

        boolean flag = true;
        System.out.println(Boolean.TRUE);
        System.out.println(flag == Boolean.TRUE);
        //flag = false;
        System.out.println(flag == Boolean.TRUE);
        System.out.println(flag == Boolean.FALSE);

        final File file = new File("YES" + Long.toString(new Date().getTime()) + ".pdf");
        if (!file.exists()) {
            file.createNewFile();
        }
        try (final FileOutputStream fos = new FileOutputStream(file);) {

            new ByteArrayOutputStream().writeTo(fos);
            System.out.println(file.getAbsolutePath());
            return file;
        } finally {
            if (file != null) {
                file.delete();
            }
        }

        // System.out.println(messages.stream().map(m -> this.getFullMessage(m)).collect(Collectors.toList()));
        // System.out.println(messages.isEmpty() + " -- is Empty");
        // System.out.println(messages.size() + " -- is Empty");
        // // messages.removeAll(messages);
        // System.out.println(messages.isEmpty() + " -- is Empty");
        // System.out.println(messages.size() + " -- is Empty");
        // if (!messages.isEmpty() && messages.size() != 0) {
        // System.out.println("it worked");
        // }

        // System.out.println(collect); // output : {2=heroku.com}

        // m -> m.getMessage()).collect(Collectors.toList()));
        // commissioningInvoices.stream()
        // .map(
        // ci -> convertToDealerSummaryDTO(ci)
        // )
        // .collect(Collectors.toList());

        // try (BufferedReader br = new BufferedReader(new FileReader("C:\\journaldev.txt"));
        // java.io.BufferedWriter writer = java.nio.file.Files.newBufferedWriter(FileSystems.getDefault().getPath("C:\\journaldev.txt"), Charset.defaultCharset())) {
        // System.out.println(br.readLine());
        // } catch (final IOException e) {
        // e.printStackTrace();
        // }
    }

    private void getString(String connResponse) {
        connResponse = "hello string";
        if (Boolean.TRUE.equals(connResponse == null)) {
            System.out.println(" inside if loop");
        }
    }

    private void prepareMessages() {

        messages.add(buildMessage(1, "Charan1", "desc 1"));
        messages.add(buildMessage(2, "Charan2", "desc 2"));
        messages.add(buildMessage(3, "Charan3", "desc 3"));
    }

    Message buildMessage(int id, String message, String desc) {
        return Message.builder().id(id).desc(desc).message(message).build();

    }

    private String getMessageDesc(int msgId) {
        // System.out.println("inside method : " + msgId + "---> " + messages.get(msgId).getDesc());
        return messages.get(msgId - 1).getDesc();

    }

    private String getMessageDesc1() {
        return messages.get(2).getDesc();

    }

    void testLambda() {
        final List<Message> localMsgs = messages;
        // System.out.println(localMsgs);
        prepareMessages();
        // System.out.println(localMsgs);
        localMsgs.get(0).setDesc("test once m,ore");
        System.out.println(messages);
        System.out.println(localMsgs);
        // System.out.println(this ::getMessageDesc1);
        System.out.println(getMessageDesc1());
        final List<Integer> ids = messages.stream().map(Message::getId).collect(Collectors.toList());
        System.out.println(ids);
        System.out.println(ids.stream().map(this::getMessageDesc).collect(Collectors.toList()));

    }

    private Set<String> extractKeywords(String templateString) {
        final Set<String> templateSet = new HashSet<>();

        final java.util.regex.Pattern p = java.util.regex.Pattern.compile(JavaTest.KEYWORD_PATTERN);
        final java.util.regex.Matcher m = p.matcher(templateString);
        while (m.find()) {
            templateSet.add(m.group());
        }
        return templateSet;
    }

    private static String prepareFormattedString(List<String> phList, List<String> simList) {

        for (final String ss : phList) {
            System.out.print(ss + System.lineSeparator());

        }
        System.out.println();
        final StringBuilder stb = new StringBuilder();
        stb.append("telephone/simNo").append("\n");
        if (phList.size() == simList.size()) {
            for (int i = 0; i < phList.size(); i++) {
                stb.append(phList.get(i)).append("//").append(simList.get(i));
                stb.append("\n");
            }
        }
        return stb.toString();
    }

}

enum UsageTypeCodes {
    //@formatter:off
    SPRAAK("Spraak"),
    DATA("Data");
  //@formatter:on
    private String description;

    private UsageTypeCodes(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}

//
@Builder
@Data
@AllArgsConstructor
class Message {
    int id;
    String message;
    String desc;
}

//
// Message() {
// System.out.println("inside super class");
// }
// // public String getFullMessage() {
// // return message + desc;
// // }
//
// String getFullMessage1(String message, String desc) {
// return message + desc;
// }
//
// }
//
// @Data
// class ChildMessage extends Message {
// int id;
// String message;
// String desc;
//
// ChildMessage(Double i) {
//
// }
//
// // public String getFullMessage() {
// // return message + desc;
// // }
//
// @Override
// String getFullMessage1(String message, String desc) {
// return message + desc;
// }
//
// }

@AllArgsConstructor
@Data
class Item {

    private final String name;
    private final int qty;
    private final BigDecimal price;

    // constructors, getter/setters
}