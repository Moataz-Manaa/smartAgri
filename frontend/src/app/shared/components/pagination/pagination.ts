import { Component, EventEmitter, Input, Output } from '@angular/core';

@Component({
  selector: 'app-pagination',
  templateUrl: './pagination.html',
})
export class Pagination {
  @Input() page = 0;
  @Input() taille = 10;
  @Input() totalElements = 0;
  @Input() totalPages = 0;
  @Input() desactive = false;
  @Output() pageChange = new EventEmitter<number>();
  @Output() tailleChange = new EventEmitter<number>();

  changerPage(page: number): void {
    if (!this.desactive && page >= 0 && page < this.totalPages && page !== this.page) {
      this.pageChange.emit(page);
    }
  }

  changerTaille(event: Event): void {
    if (!this.desactive) {
      this.tailleChange.emit(Number((event.target as HTMLSelectElement).value));
    }
  }
}
