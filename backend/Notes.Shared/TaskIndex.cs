using System.Globalization;
using Notes.Shared.Model;

namespace Notes.Shared
{
    public static class TaskIndex
    {
        /// <summary>
        /// Returns a list of TaskRow objects for the given note, representing tasks with due dates extracted from the note's body.
        /// </summary>
        /// <param name="note">Note object to parse tasks from</param>
        /// <returns>List of TaskRow objects</returns>
        public static IReadOnlyList<TaskRow> TasksOf(Note note)
        {
            return TasksOf(note.type, note.body, note.id);
        }

        /// <summary>
        /// Returns a list of TaskRow objects for the given note, representing tasks with due dates extracted from the note's body.
        /// </summary>
        /// <param name="type">Note type</param>
        /// <param name="body">Body of the note</param>
        /// <param name="id">Id of the note</param>
        /// <returns>List of TaskRow objects</returns>
        public static IReadOnlyList<TaskRow> TasksOf(NoteType type, string body, Guid id)
        {
            if (type == NoteType.Note) { return new List<TaskRow>(); }
            List<TaskRow> tasks = new List<TaskRow>();
            IReadOnlyList<ChecklistItem> checklistItems = ChecklistParser.ParseChecklist(body);
            for (int index = 0; index < checklistItems.Count; index++)
            {
                ChecklistItem item = checklistItems[index];
                if (item.due.HasValue)
                {
                    tasks.Add(new TaskRow()
                    {
                        noteId = id,
                        text = item.text.Trim(),
                        due = item.due.Value.ToString("yyyy-MM-dd", CultureInfo.InvariantCulture),
                        @checked = item.@checked,
                        position = index, //note position within all checklist items, not just dated ones
                    });
                }
            }
            return tasks;
        }
    }
}
