import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Objects } from './objects';

describe('Objects', () => {
  let component: Objects;
  let fixture: ComponentFixture<Objects>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [Objects],
    }).compileComponents();

    fixture = TestBed.createComponent(Objects);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
