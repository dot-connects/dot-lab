// State of one new book while the person walks the new-book screens. Plain data, no plugins.
import '../../core/models.dart' show BookOptions;
import '../../pipeline.dart' show NoteInput;

class Album {
  final String id, name;
  final int inRange, total;
  final String? thumb;
  const Album(this.id, this.name, {required this.inRange, required this.total, this.thumb});
}

class PhotoRef {
  final String id, path;
  const PhotoRef(this.id, this.path);
}

class NoteItem {
  final String title, detail;
  final String? thumb; // handwriting photo
  final bool bad; // could not be read: shown, but left out of the book
  final NoteInput? input; // what goes to the AI
  const NoteItem(this.title, this.detail, {this.thumb, this.bad = false, this.input});
}

const maxMustInclude = 10;

class NewBookDraft {
  DateTime? start, end;
  Set<String> albums = {};
  Set<String> mustInclude = {};
  List<NoteItem> notes = [];
  String? timeline; // Timeline.json path
  BookOptions opts = BookOptions();
  bool shapeTouched = false; // came back from the book-shape screen

  /// Toggle a must-include photo. Returns false (no change) when it would be the 11th.
  bool toggleMust(String id) {
    if (mustInclude.remove(id)) return true;
    if (mustInclude.length >= maxMustInclude) return false;
    mustInclude.add(id);
    return true;
  }
}

const themeNames = {'a': '여백의 책', 'b': '기록 카드', 'c': '여행 수첩', 'diary': '일기', 'mag': '잡지', 'album': '앨범'};
const lengthNames = {'short': '짧게', 'normal': '보통', 'long': '길게'};
const lengthPages = {'short': 16, 'normal': 24, 'long': 36};
