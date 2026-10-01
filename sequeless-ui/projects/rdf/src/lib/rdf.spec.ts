import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Rdf } from './rdf';

describe('Rdf', () => {
  let component: Rdf;
  let fixture: ComponentFixture<Rdf>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [Rdf],
    }).compileComponents();

    fixture = TestBed.createComponent(Rdf);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
