package io.rsocket.graphql;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import graphql.schema.DataFetcher;
import java.awt.print.Book;
import java.util.HashMap;
import java.util.Map;

public class GraphQLDataFetchers {

  private static final Map<String, Author> authors;

  private static final Map<String, Book> books;

  static {
    authors = new HashMap<>();
    authors.put("author-1", new Author("author-1", "Joanne", "Rowling"));
    authors.put("author-2", new Author("author-2", "Herman", "Melville"));
    authors.put("author-3", new Author("author-3", "Anne", "Rice"));

    books = new HashMap<>();
    books.put(
        "book-1",
        new Book(
            "book-1", "Harry Potter and the Philosopher's Stone", 223, authors.get("author-1")));
    books.put("book-2", new Book("book-2", "Moby Dick", 635, authors.get("author-2")));
    books.put(
        "book-3", new Book("book-3", "Interview with the vampire", 371, authors.get("author-3")));
  }

  public static DataFetcher<Book> getBookByIdDataFetcher() {
    return dataFetchingEnvironment -> {
      String bookId = dataFetchingEnvironment.getArgument("id");
      System.out.println("looking for book id " + bookId);
      Book book = books.get(bookId);

      return book;
    };
  }

  public static DataFetcher<Author> getAuthorDataFetcher() {
    return dataFetchingEnvironment -> {
      Book book = dataFetchingEnvironment.getSource();
      String authorId = book.getAuthor().getId();
      System.out.println("looking for author id " + authorId);
      return authors.get(authorId);
    };
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class Book {
    String id;
    String name;
    int pageCount;
    Author author;

    public Book() {}

    public Book(String id, String name, int pageCount, Author author) {
      this.id = id;
      this.name = name;
      this.pageCount = pageCount;
      this.author = author;
    }

    public String getId() {
      return id;
    }

    public void setId(String id) {
      this.id = id;
    }

    public String getName() {
      return name;
    }

    public void setName(String name) {
      this.name = name;
    }

    public int getPageCount() {
      return pageCount;
    }

    public void setPageCount(int pageCount) {
      this.pageCount = pageCount;
    }

    public Author getAuthor() {
      return author;
    }

    public void setAuthor(Author author) {
      this.author = author;
    }

    @Override
    public String toString() {
      return "Book{"
          + "id='"
          + id
          + '\''
          + ", name='"
          + name
          + '\''
          + ", pageCount="
          + pageCount
          + ", author="
          + author
          + '}';
    }
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class Author {
    String id;
    String firstName;
    String lastName;

    public Author() {}

    public Author(String id, String firstName, String lastName) {
      this.id = id;
      this.firstName = firstName;
      this.lastName = lastName;
    }

    public String getId() {
      return id;
    }

    public void setId(String id) {
      this.id = id;
    }

    public String getFirstName() {
      return firstName;
    }

    public void setFirstName(String firstName) {
      this.firstName = firstName;
    }

    public String getLastName() {
      return lastName;
    }

    public void setLastName(String lastName) {
      this.lastName = lastName;
    }

    @Override
    public String toString() {
      return "Author{"
          + "id='"
          + id
          + '\''
          + ", firstName='"
          + firstName
          + '\''
          + ", lastName='"
          + lastName
          + '\''
          + '}';
    }
  }
}
